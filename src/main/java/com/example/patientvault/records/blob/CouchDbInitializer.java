package com.example.patientvault.records.blob;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Idempotent CouchDB set-up with the admin account: system databases, the file database, the dedicated
 * member account (password re-applied every start so it follows the environment), the database security
 * document and the Mango index used only by the reconciliation job. If CouchDB is not reachable at start the
 * application still starts; set-up is retried every 30 seconds and readiness stays DOWN until it succeeds.
 */
public class CouchDbInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(CouchDbInitializer.class);

    private final CouchDbProperties properties;
    private final CouchDbClient admin;
    private final AtomicBoolean initialized = new AtomicBoolean();

    public CouchDbInitializer(CouchDbProperties properties, CouchDbClient admin) {
        this.properties = properties;
        this.admin = admin;
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    @Override
    public void run(ApplicationArguments args) {
        tryInitialize();
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    void retryUntilInitialized() {
        if (!initialized.get()) {
            tryInitialize();
        }
    }

    synchronized void tryInitialize() {
        if (initialized.get()) {
            return;
        }
        try {
            initialize();
            initialized.set(true);
            LOG.info("CouchDB initialised (database '{}')", properties.database());
        } catch (RuntimeException e) {
            LOG.error("CouchDB initialisation failed, will retry: {}", e.getMessage());
        }
    }

    private void initialize() {
        String db = "/" + properties.database();
        ensureDatabase("/_users");
        ensureDatabase("/_replicator");
        ensureDatabase(db);
        ensureAppUser();

        ObjectNode security = admin.mapper().createObjectNode();
        ObjectNode admins = security.putObject("admins");
        admins.putArray("names");
        admins.putArray("roles");
        ObjectNode members = security.putObject("members");
        members.putArray("names").add(properties.appUser());
        members.putArray("roles");
        require(admin.json("PUT", db + "/_security", security, true), "security document");

        String index = "{\"index\":{\"fields\":[\"patientId\"]},\"name\":\"by-patient\",\"type\":\"json\"}";
        require(admin.json("POST", db + "/_index", index, true), "index");
    }

    private void ensureDatabase(String path) {
        CouchDbClient.Reply reply = admin.json("PUT", path, null, true);
        if (reply.status() != 412 && !reply.ok()) { // 412 = already exists
            throw new BlobStoreException("Could not create database " + path + " (" + reply.status() + ")");
        }
    }

    private void ensureAppUser() {
        String path = "/_users/org.couchdb.user:" + URLEncoder.encode(properties.appUser(), StandardCharsets.UTF_8);
        CouchDbClient.Reply existing = admin.json("GET", path, null, true);
        ObjectNode user = admin.mapper().createObjectNode();
        if (existing.ok()) {
            JsonNode rev = existing.body().path("_rev");
            user.put("_rev", rev.asString());
        } else if (existing.status() != 404) {
            throw new BlobStoreException("Could not look up the application user (" + existing.status() + ")");
        }
        user.put("name", properties.appUser());
        user.put("type", "user");
        user.putArray("roles");
        user.put("password", properties.appPassword());
        require(admin.json("PUT", path, user, true), "application user");
    }

    private static void require(CouchDbClient.Reply reply, String what) {
        if (!reply.ok()) {
            throw new BlobStoreException("Could not set up " + what + " (" + reply.status() + ")");
        }
    }
}
