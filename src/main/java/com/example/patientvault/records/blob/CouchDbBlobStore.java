package com.example.patientvault.records.blob;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * CouchDB adapter: one document per file with the encrypted bytes as the {@code content} attachment and an
 * optional {@code thumb} attachment. The document contains no personal data. Normal reads and writes use the
 * dedicated member account ({@code app}); purge, compaction and index maintenance need server-admin rights in
 * CouchDB and go through {@code admin}.
 */
public class CouchDbBlobStore implements BlobStore {

    static final String CONTENT = "content";
    static final String THUMB = "thumb";
    private static final Logger LOG = LoggerFactory.getLogger(CouchDbBlobStore.class);
    private static final int PAGE = 1000;

    private final CouchDbClient app;
    private final CouchDbClient admin;
    private final String db;

    public CouchDbBlobStore(CouchDbClient app, CouchDbClient admin, String database) {
        this.app = app;
        this.admin = admin;
        this.db = "/" + database;
    }

    @Override
    public BlobRef put(UUID fileId, UUID patientId, InputStream encryptedContent, long ciphertextLength, BlobMeta meta) {
        String docId = fileId.toString();
        String docPath = db + "/" + docId;
        try {
            ObjectNode doc = app.mapper().createObjectNode();
            doc.put("type", "medical-file");
            doc.put("patientId", patientId.toString());
            doc.put("keyId", meta.keyId());
            doc.put("cipher", meta.cipher());
            doc.put("plaintextSize", meta.plaintextSize());
            doc.put("createdAt", meta.createdAt().toString());
            CouchDbClient.Reply created = app.json("PUT", docPath, doc, false);
            if (created.status() != 201) {
                throw new BlobStoreException("CouchDB refused to create the document (" + created.status() + ")");
            }
            String rev = created.body().path("rev").asString();
            CouchDbClient.Reply attached = app.upload(docPath + "/" + CONTENT + "?rev=" + rev, encryptedContent, ciphertextLength);
            if (attached.status() != 201) {
                throw new BlobStoreException("CouchDB refused the attachment (" + attached.status() + ")");
            }
            return new BlobRef(docId, attached.body().path("rev").asString());
        } catch (RuntimeException e) {
            // compensation: never leave a half-created document behind
            try {
                erase(BlobRef.of(fileId));
            } catch (RuntimeException cleanup) {
                LOG.warn("Could not clean up partially stored blob {}", docId);
            }
            throw e;
        }
    }

    @Override
    public void putThumbnail(BlobRef ref, InputStream encryptedThumb, long length) {
        String docPath = db + "/" + ref.docId();
        String rev = app.currentRev(docPath);
        if (rev == null) {
            throw new BlobNotFoundException();
        }
        CouchDbClient.Reply reply = app.upload(docPath + "/" + THUMB + "?rev=" + rev, encryptedThumb, length);
        if (reply.status() != 201) {
            throw new BlobStoreException("CouchDB refused the thumbnail (" + reply.status() + ")");
        }
    }

    @Override
    public InputStream openContent(BlobRef ref) {
        CouchDbClient.StreamReply reply = app.stream(db + "/" + ref.docId() + "/" + CONTENT);
        if (reply.status() == 404) {
            throw new BlobNotFoundException();
        }
        if (reply.body() == null) {
            throw new BlobStoreException("CouchDB answered " + reply.status());
        }
        return reply.body();
    }

    @Override
    public Optional<InputStream> openThumbnail(BlobRef ref) {
        CouchDbClient.StreamReply reply = app.stream(db + "/" + ref.docId() + "/" + THUMB);
        if (reply.status() == 404) {
            return Optional.empty();
        }
        if (reply.body() == null) {
            throw new BlobStoreException("CouchDB answered " + reply.status());
        }
        return Optional.of(reply.body());
    }

    /** DELETE + _purge. Both steps tolerate "already gone", so a retry after a crash simply finishes the job. */
    @Override
    public void erase(BlobRef ref) {
        String docPath = db + "/" + ref.docId();
        String rev = app.currentRev(docPath);
        if (rev == null) {
            return;
        }
        CouchDbClient.Reply deleted = app.json("DELETE", docPath + "?rev=" + rev, null, false);
        String tombstone;
        if (deleted.status() == 200 || deleted.status() == 201 || deleted.status() == 202) {
            tombstone = deleted.body().path("rev").asString();
        } else if (deleted.status() == 404) {
            return;
        } else {
            throw new BlobStoreException("CouchDB could not delete the document (" + deleted.status() + ")");
        }
        ObjectNode purge = admin.mapper().createObjectNode();
        purge.putArray(ref.docId()).add(tombstone);
        CouchDbClient.Reply purged = admin.json("POST", db + "/_purge", purge, false);
        if (!purged.ok()) {
            throw new BlobStoreException("CouchDB could not purge the document (" + purged.status() + ")");
        }
    }

    @Override
    public void compact() {
        CouchDbClient.Reply reply = admin.json("POST", db + "/_compact", "{}", false);
        if (!reply.ok()) {
            throw new BlobStoreException("CouchDB could not start compaction (" + reply.status() + ")");
        }
    }

    @Override
    public Set<String> listAllDocumentIds() {
        Set<String> ids = new HashSet<>();
        String startKey = null;
        while (true) {
            String path = db + "/_all_docs?limit=" + (PAGE + 1)
                    + (startKey == null ? "" : "&startkey=" + java.net.URLEncoder.encode("\"" + startKey + "\"", java.nio.charset.StandardCharsets.UTF_8));
            CouchDbClient.Reply reply = app.json("GET", path, null, true);
            if (!reply.ok()) {
                throw new BlobStoreException("CouchDB could not list documents (" + reply.status() + ")");
            }
            JsonNode rows = reply.body().path("rows");
            int count = 0;
            String next = null;
            for (JsonNode row : rows) {
                String id = row.path("id").asString();
                if (count++ == PAGE) {
                    next = id;
                    break;
                }
                if (!id.startsWith("_design/")) {
                    ids.add(id);
                }
            }
            if (next == null) {
                return ids;
            }
            startKey = next;
        }
    }

    /** Reachability and permission check used by the health indicator: the app account can read the database. */
    boolean reachable() {
        try {
            return app.json("GET", db, null, false).ok();
        } catch (BlobStoreException e) {
            return false;
        }
    }

    boolean looksHealthy() {
        return app.looksHealthy();
    }

    long lastLatencyMillis() {
        return app.lastLatencyMillis();
    }
}
