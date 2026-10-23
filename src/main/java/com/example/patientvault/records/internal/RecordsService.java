package com.example.patientvault.records.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;
import com.example.patientvault.crypto.CryptoService;
import com.example.patientvault.crypto.DataKey;
import com.example.patientvault.crypto.IntegrityFailureException;
import com.example.patientvault.records.FolderListing;
import com.example.patientvault.shared.PatientAccess;
import com.example.patientvault.records.RecordsApi;
import com.example.patientvault.records.blob.BlobMeta;
import com.example.patientvault.records.blob.BlobNotFoundException;
import com.example.patientvault.records.blob.BlobRef;
import com.example.patientvault.records.blob.BlobStore;
import com.example.patientvault.shared.Ids;
import com.example.patientvault.shared.Permissions;

/**
 * Medical files and folders of a patient. Every method checks the permission, that the patient is ACTIVE and
 * that the folder/file belongs to that patient (IDOR). Names are encrypted per patient, file content is
 * stream-encrypted and stored in the object store; PostgreSQL only holds metadata. Nothing here logs names.
 */
@Service
public class RecordsService implements RecordsApi {

    private static final Logger LOG = LoggerFactory.getLogger(RecordsService.class);
    private static final String CIPHER = "AES256_GCM_HKDF_1MB";
    private static final int MAX_FOLDER_OPTIONS = 1000;

    public record UploadResult(UUID fileId, boolean hasThumbnail) {
    }

    private final MedicalFileRepository files;
    private final MedicalFolderRepository folders;
    private final BlobStore blobs;
    private final CryptoService crypto;
    private final PatientAccess access;
    private final UploadPolicy policy;
    private final ThumbnailService thumbnails;
    private final UploadProperties properties;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final ConcurrentHashMap<UUID, Object> patientLocks = new ConcurrentHashMap<>();

    RecordsService(MedicalFileRepository files, MedicalFolderRepository folders, BlobStore blobs, CryptoService crypto,
                   PatientAccess access, UploadPolicy policy, ThumbnailService thumbnails, UploadProperties properties,
                   AuditService audit, Clock clock, PlatformTransactionManager transactionManager) {
        this.files = files;
        this.folders = folders;
        this.blobs = blobs;
        this.crypto = crypto;
        this.access = access;
        this.policy = policy;
        this.thumbnails = thumbnails;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ---- browse -------------------------------------------------------------------------------

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.FILE_READ + "')")
    @Transactional(readOnly = true)
    public FolderListing browse(UUID patientId, UUID folderId) {
        requireActive(patientId);
        DataKey key = access.dataKey(patientId);
        Tree tree = tree(patientId, key);
        if (folderId != null && !tree.contains(folderId)) {
            throw RecordsException.notFound();
        }
        Map<UUID, Long> fileCounts = new HashMap<>();
        files.countAvailableByFolder(patientId).forEach(row -> fileCounts.put((UUID) row[0], (Long) row[1]));

        List<FolderListing.FolderItem> subfolders = tree.children(folderId).stream()
                .map(f -> new FolderListing.FolderItem(f.getId(), tree.name(f),
                        tree.children(f.getId()).size() + fileCounts.getOrDefault(f.getId(), 0L).intValue()))
                .sorted(Comparator.comparing(i -> sortKey(i.name())))
                .toList();

        List<FolderListing.FileItem> items = files.findInFolder(patientId, folderId, List.of(MedicalFile.AVAILABLE)).stream()
                .map(f -> new FolderListing.FileItem(f.getId(), fileName(key, f), f.getCategory(), f.getMimeType(),
                        f.getSizeBytes(), f.isHasThumbnail(), UploadPolicy.isViewableInline(f.getMimeType()), f.getUploadedAt()))
                .sorted(Comparator.comparing(i -> sortKey(i.name())))
                .toList();

        return new FolderListing(patientId, folderId, tree.crumbs(folderId), subfolders, items, tree.options());
    }

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.FILE_READ + "')")
    @Transactional(readOnly = true)
    public Map<UUID, Long> countFiles(Collection<UUID> patientIds) {
        if (patientIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        files.countAvailableByPatient(patientIds).forEach(row -> counts.put((UUID) row[0], (Long) row[1]));
        return counts;
    }

    // ---- upload -------------------------------------------------------------------------------

    /**
     * One file: inspect, resolve/create folders, register a PENDING row, stream the encrypted content to the object
     * store (with a thumbnail for photos) and mark the file AVAILABLE. On any failure the blob and the row are removed.
     */
    @PreAuthorize("hasAuthority('" + Permissions.FILE_WRITE + "')")
    public UploadResult upload(UUID patientId, UUID baseFolderId, String relativePath, UploadSource source) {
        requireActive(patientId);
        DataKey key = access.dataKey(patientId);
        String name = FileNames.sanitize(source.originalName());
        UploadPolicy.Detected detected;
        try {
            if (source.size() <= 0) {
                throw RecordsException.badRequest("records.error.empty");
            }
            if (source.size() > properties.maxFileSize().toBytes()) {
                throw new RecordsException(413, "records.error.tooLarge", properties.maxFileSize().toMegabytes());
            }
            detected = policy.inspect(readHeader(source), name);
        } catch (RecordsException e) {
            audit.record(AuditEvent.failure(AuditAction.FILE_UPLOADED).onEntity("PATIENT", patientId)
                    .withDetail("reason", e.messageKey()));
            throw e;
        }
        List<String> segments = FileNames.folderSegments(relativePath, properties.maxFolderDepth());

        UUID fileId = Ids.newId();
        synchronized (patientLocks.computeIfAbsent(patientId, id -> new Object())) {
            UUID folderId = tx.execute(status -> {
                UUID target = resolveFolderChain(patientId, baseFolderId, segments, key);
                String unique = uniqueFileName(patientId, target, name, key, null);
                MedicalFile pending = new MedicalFile(fileId, patientId, target, detected.category(),
                        crypto.encryptText(key, fileField(fileId), unique), detected.mimeType(), source.size());
                files.saveAndFlush(pending);
                return target;
            });
            LOG.debug("Upload registered for file {} in folder {}", fileId, folderId);
        }

        try {
            return store(patientId, fileId, key, detected, source);
        } catch (RuntimeException e) {
            discard(fileId);
            audit.record(AuditEvent.failure(AuditAction.FILE_UPLOADED).onEntity("FILE", fileId)
                    .withDetail("patientId", patientId));
            throw e;
        }
    }

    private UploadResult store(UUID patientId, UUID fileId, DataKey key, UploadPolicy.Detected detected, UploadSource source) {
        MessageDigest digest = sha256();
        BlobRef ref;
        try (InputStream plain = source.open();
             InputStream encrypted = new DigestInputStream(
                     crypto.encryptStream(key, fileId, CryptoService.PURPOSE_CONTENT, plain), digest)) {
            ref = blobs.put(fileId, patientId, encrypted, crypto.encryptedSize(source.size()),
                    new BlobMeta(keyIdOf(), CIPHER, source.size(), clock.instant()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        boolean thumbnail = false;
        if ("PHOTO".equals(detected.category())) {
            Optional<byte[]> bytes = thumbnails.create(source::open);
            if (bytes.isPresent()) {
                try (InputStream encrypted = crypto.encryptStream(key, fileId, CryptoService.PURPOSE_THUMBNAIL,
                        new java.io.ByteArrayInputStream(bytes.get()))) {
                    blobs.putThumbnail(ref, encrypted, crypto.encryptedSize(bytes.get().length));
                    thumbnail = true;
                } catch (IOException | RuntimeException e) {
                    LOG.warn("Thumbnail for file {} could not be stored: {}", fileId, e.getClass().getSimpleName());
                }
            }
        }

        boolean hasThumbnail = thumbnail;
        byte[] sha = digest.digest();
        tx.executeWithoutResult(status -> {
            MedicalFile file = files.findById(fileId).orElseThrow();
            file.setCiphertextSha256(sha);
            file.setHasThumbnail(hasThumbnail);
            file.setStatus(MedicalFile.AVAILABLE);
        });
        audit.record(AuditEvent.success(AuditAction.FILE_UPLOADED).onEntity("FILE", fileId)
                .withDetail("patientId", patientId).withDetail("category", detected.category())
                .withDetail("bytes", source.size()));
        return new UploadResult(fileId, hasThumbnail);
    }

    /** Removes whatever a failed upload left behind (blob and PENDING row). Best effort; reconciliation cleans the rest. */
    private void discard(UUID fileId) {
        try {
            blobs.erase(BlobRef.of(fileId));
        } catch (RuntimeException e) {
            LOG.warn("Could not remove blob of failed upload {}", fileId);
        }
        try {
            tx.executeWithoutResult(status -> files.findById(fileId).ifPresent(files::delete));
        } catch (RuntimeException e) {
            LOG.warn("Could not remove row of failed upload {}", fileId);
        }
    }

    private byte[] readHeader(UploadSource source) {
        try (InputStream in = source.open()) {
            return in.readNBytes(UploadPolicy.SNIFF_BYTES);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- reading content ----------------------------------------------------------------------

    /**
     * Opens a file for display or download. Images and PDFs are shown inline unless {@code download} is set; the
     * read is audited as FILE_VIEWED or FILE_DOWNLOADED accordingly. The first cipher segment is verified before
     * this returns.
     */
    @PreAuthorize("hasAuthority('" + Permissions.FILE_READ + "')")
    public FileContent open(UUID patientId, UUID fileId, boolean download) {
        requireActive(patientId);
        DataKey key = access.dataKey(patientId);
        MedicalFile file = files.findByIdAndPatientId(fileId, patientId)
                .filter(f -> MedicalFile.AVAILABLE.equals(f.getStatus())).orElseThrow(RecordsException::notFound);
        boolean inline = !download && UploadPolicy.isViewableInline(file.getMimeType());
        FileContent content = openStream(file, key, inline);
        audit.record(AuditEvent.success(inline ? AuditAction.FILE_VIEWED : AuditAction.FILE_DOWNLOADED)
                .onEntity("FILE", fileId).withDetail("patientId", patientId));
        return content;
    }

    /** The decrypted thumbnail bytes (JPEG) or empty when the file has none. Not audited (a grid shows many). */
    @PreAuthorize("hasAuthority('" + Permissions.FILE_READ + "')")
    public Optional<byte[]> thumbnail(UUID patientId, UUID fileId) {
        requireActive(patientId);
        DataKey key = access.dataKey(patientId);
        MedicalFile file = files.findByIdAndPatientId(fileId, patientId)
                .filter(f -> MedicalFile.AVAILABLE.equals(f.getStatus())).orElseThrow(RecordsException::notFound);
        if (!file.isHasThumbnail()) {
            return Optional.empty();
        }
        Optional<InputStream> stored = blobs.openThumbnail(BlobRef.of(file.getId()));
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream plain = crypto.decryptStream(key, file.getId(), CryptoService.PURPOSE_THUMBNAIL, stored.get())) {
            return Optional.of(plain.readAllBytes());
        } catch (IntegrityFailureException e) {
            reportIntegrityFailure(patientId, fileId);
            throw new FileIntegrityException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private FileContent openStream(MedicalFile file, DataKey key, boolean inline) {
        InputStream stored = null;
        try {
            stored = blobs.openContent(BlobRef.of(file.getId()));
            PushbackInputStream plain = new PushbackInputStream(
                    crypto.decryptStream(key, file.getId(), CryptoService.PURPOSE_CONTENT, stored), 1);
            int first = plain.read();               // forces decryption + authentication of the first segment
            if (first >= 0) {
                plain.unread(first);
            }
            UUID patientId = file.getPatientId();
            UUID fileId = file.getId();
            return new FileContent(fileId, fileName(key, file), file.getMimeType(), file.getCategory(),
                    file.getSizeBytes(), inline, plain, () -> reportIntegrityFailure(patientId, fileId));
        } catch (IntegrityFailureException e) {
            closeQuietly(stored);
            reportIntegrityFailure(file.getPatientId(), file.getId());
            throw new FileIntegrityException(e);
        } catch (BlobNotFoundException e) {
            throw RecordsException.notFound();
        } catch (IOException e) {
            closeQuietly(stored);
            throw new UncheckedIOException(e);
        }
    }

    private void reportIntegrityFailure(UUID patientId, UUID fileId) {
        audit.record(AuditEvent.failure(AuditAction.FILE_INTEGRITY_FAILURE).onEntity("FILE", fileId)
                .withDetail("patientId", patientId));
    }

    // ---- folders ------------------------------------------------------------------------------

    @PreAuthorize("hasAuthority('" + Permissions.FILE_WRITE + "')")
    public UUID createFolder(UUID patientId, UUID parentId, String rawName) {
        requireActive(patientId);
        DataKey key = access.dataKey(patientId);
        String name = FileNames.sanitizeFolderName(rawName);
        if (name.isEmpty()) {
            throw RecordsException.badRequest("records.error.name.required");
        }
        synchronized (patientLocks.computeIfAbsent(patientId, id -> new Object())) {
            UUID id = tx.execute(status -> {
                Tree tree = tree(patientId, key);
                if (parentId != null && !tree.contains(parentId)) {
                    throw RecordsException.notFound();
                }
                if (tree.depth(parentId) + 1 > properties.maxFolderDepth()) {
                    throw RecordsException.badRequest("records.error.depth", properties.maxFolderDepth());
                }
                if (tree.hasChildNamed(parentId, name, null)) {
                    throw RecordsException.conflict("records.error.name.duplicate");
                }
                return createFolderRow(patientId, parentId, name, key);
            });
            audit.record(AuditEvent.success(AuditAction.FOLDER_CREATED).onEntity("FOLDER", id).withDetail("patientId", patientId));
            return id;
        }
    }

    @PreAuthorize("hasAuthority('" + Permissions.FILE_WRITE + "')")
    @Transactional
    public void renameFolder(UUID patientId, UUID folderId, String rawName) {
        requireActive(patientId);
        DataKey key = access.dataKey(patientId);
        String name = FileNames.sanitizeFolderName(rawName);
        if (name.isEmpty()) {
            throw RecordsException.badRequest("records.error.name.required");
        }
        Tree tree = tree(patientId, key);
        MedicalFolder folder = tree.get(folderId).orElseThrow(RecordsException::notFound);
        if (tree.hasChildNamed(folder.getParentId(), name, folderId)) {
            throw RecordsException.conflict("records.error.name.duplicate");
        }
        folder.setNameEnc(crypto.encryptText(key, folderField(folderId), name));
        audit.record(AuditEvent.success(AuditAction.FOLDER_RENAMED).onEntity("FOLDER", folderId).withDetail("patientId", patientId));
    }

    @PreAuthorize("hasAuthority('" + Permissions.FILE_WRITE + "')")
    @Transactional
    public void moveFolder(UUID patientId, UUID folderId, UUID targetParentId) {
        requireActive(patientId);
        DataKey key = access.dataKey(patientId);
        Tree tree = tree(patientId, key);
        MedicalFolder folder = tree.get(folderId).orElseThrow(RecordsException::notFound);
        if (targetParentId != null && !tree.contains(targetParentId)) {
            throw RecordsException.notFound();
        }
        if (java.util.Objects.equals(folder.getParentId(), targetParentId)) {
            return;
        }
        if (folderId.equals(targetParentId) || (targetParentId != null && tree.isDescendant(targetParentId, folderId))) {
            throw RecordsException.badRequest("records.error.move.into.itself");
        }
        if (tree.depth(targetParentId) + 1 + tree.height(folderId) > properties.maxFolderDepth()) {
            throw RecordsException.badRequest("records.error.depth", properties.maxFolderDepth());
        }
        if (tree.hasChildNamed(targetParentId, tree.name(folder), folderId)) {
            throw RecordsException.conflict("records.error.name.duplicate");
        }
        folder.setParentId(targetParentId);
        audit.record(AuditEvent.success(AuditAction.FOLDER_MOVED).onEntity("FOLDER", folderId).withDetail("patientId", patientId));
    }

    /** Deletes a folder with everything below it: blobs first, then rows. A blob failure leaves the rest to be retried. */
    @PreAuthorize("hasAuthority('" + Permissions.FILE_WRITE + "')")
    public void deleteFolder(UUID patientId, UUID folderId) {
        requireActive(patientId);
        DataKey key = access.dataKey(patientId);
        synchronized (patientLocks.computeIfAbsent(patientId, id -> new Object())) {
            Tree tree = tree(patientId, key);
            if (!tree.contains(folderId)) {
                throw RecordsException.notFound();
            }
            Set<UUID> subtree = tree.subtree(folderId);
            List<MedicalFile> inside = new ArrayList<>(files.findByPatientIdAndFolderIdIn(patientId, subtree));
            tx.executeWithoutResult(status -> inside.forEach(f -> files.findById(f.getId()).ifPresent(g -> g.setStatus(MedicalFile.DELETING))));
            for (MedicalFile file : inside) {
                blobs.erase(BlobRef.of(file.getId()));
                tx.executeWithoutResult(status -> files.deleteById(file.getId()));
            }
            tx.executeWithoutResult(status -> folders.deleteById(folderId));
            audit.record(AuditEvent.success(AuditAction.FOLDER_DELETED).onEntity("FOLDER", folderId)
                    .withDetail("patientId", patientId).withDetail("files", inside.size()).withDetail("folders", subtree.size()));
        }
    }

    // ---- files --------------------------------------------------------------------------------

    @PreAuthorize("hasAuthority('" + Permissions.FILE_WRITE + "')")
    @Transactional
    public void renameFile(UUID patientId, UUID fileId, String rawName) {
        requireActive(patientId);
        DataKey key = access.dataKey(patientId);
        String name = FileNames.sanitizeFolderName(rawName);
        if (name.isEmpty()) {
            throw RecordsException.badRequest("records.error.name.required");
        }
        MedicalFile file = available(patientId, fileId);
        if (nameTaken(patientId, file.getFolderId(), name, key, fileId)) {
            throw RecordsException.conflict("records.error.name.duplicate");
        }
        file.setDisplayNameEnc(crypto.encryptText(key, fileField(fileId), name));
        audit.record(AuditEvent.success(AuditAction.FILE_RENAMED).onEntity("FILE", fileId).withDetail("patientId", patientId));
    }

    /** Moves a file; if the target folder already has that name the file gets a numeric suffix. */
    @PreAuthorize("hasAuthority('" + Permissions.FILE_WRITE + "')")
    @Transactional
    public void moveFile(UUID patientId, UUID fileId, UUID targetFolderId) {
        requireActive(patientId);
        DataKey key = access.dataKey(patientId);
        MedicalFile file = available(patientId, fileId);
        if (targetFolderId != null && folders.findByIdAndPatientId(targetFolderId, patientId).isEmpty()) {
            throw RecordsException.notFound();
        }
        if (java.util.Objects.equals(file.getFolderId(), targetFolderId)) {
            return;
        }
        String unique = uniqueFileName(patientId, targetFolderId, fileName(key, file), key, fileId);
        file.setDisplayNameEnc(crypto.encryptText(key, fileField(fileId), unique));
        file.setFolderId(targetFolderId);
        audit.record(AuditEvent.success(AuditAction.FILE_MOVED).onEntity("FILE", fileId).withDetail("patientId", patientId));
    }

    /** Marks the file DELETING, erases the blob, then removes the row. If the store fails the row stays DELETING for the retry job. */
    @PreAuthorize("hasAuthority('" + Permissions.FILE_WRITE + "')")
    public void deleteFile(UUID patientId, UUID fileId) {
        requireActive(patientId);
        MedicalFile file = files.findByIdAndPatientId(fileId, patientId).orElseThrow(RecordsException::notFound);
        tx.executeWithoutResult(status -> files.findById(file.getId()).ifPresent(f -> f.setStatus(MedicalFile.DELETING)));
        blobs.erase(BlobRef.of(file.getId()));
        tx.executeWithoutResult(status -> files.deleteById(file.getId()));
        audit.record(AuditEvent.success(AuditAction.FILE_DELETED).onEntity("FILE", fileId).withDetail("patientId", patientId));
    }

    // ---- archive ------------------------------------------------------------------------------

    /**
     * Streams a folder (null = everything) as a ZIP, decrypting each file on the fly; nothing is buffered whole.
     * Folder structure is kept, including empty folders. Entry names are the decrypted, sanitised names.
     */
    @PreAuthorize("hasAuthority('" + Permissions.FILE_READ + "')")
    public void zip(UUID patientId, UUID folderId, java.io.OutputStream out) throws IOException {
        requireActive(patientId);
        DataKey key = access.dataKey(patientId);
        Tree tree = tree(patientId, key);
        if (folderId != null && !tree.contains(folderId)) {
            throw RecordsException.notFound();
        }
        Set<UUID> folderIds = folderId == null ? new LinkedHashSet<>(tree.byId.keySet()) : tree.subtree(folderId);
        folderIds.remove(folderId);
        List<MedicalFile> toPack = new ArrayList<>(files.findByPatientIdAndFolderIdIn(patientId, folderIds));
        if (folderId == null) {
            toPack.addAll(files.findInFolder(patientId, null, List.of(MedicalFile.AVAILABLE)));
        } else {
            toPack.addAll(files.findInFolder(patientId, folderId, List.of(MedicalFile.AVAILABLE)));
        }
        toPack.removeIf(f -> !MedicalFile.AVAILABLE.equals(f.getStatus()));

        Set<String> used = new HashSet<>();
        int count = 0;
        java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out, StandardCharsets.UTF_8);
        zip.setLevel(java.util.zip.Deflater.BEST_SPEED);
        for (UUID id : folderIds) {
            String dir = tree.relativePath(id, folderId);
            if (!dir.isEmpty() && used.add(dir + "/")) {
                zip.putNextEntry(new java.util.zip.ZipEntry(dir + "/"));
                zip.closeEntry();
            }
        }
        for (MedicalFile file : toPack) {
            String dir = file.getFolderId() == null ? "" : tree.relativePath(file.getFolderId(), folderId);
            String entry = uniqueEntry(used, (dir.isEmpty() ? "" : dir + "/") + fileName(key, file));
            try (FileContent content = openStream(file, key, false)) {
                zip.putNextEntry(new java.util.zip.ZipEntry(entry));
                content.transferTo(zip);
                zip.closeEntry();
                count++;
            }
        }
        zip.finish();
        audit.record(AuditEvent.success(AuditAction.FILE_DOWNLOADED).onEntity(folderId == null ? "PATIENT" : "FOLDER",
                folderId == null ? patientId : folderId).withDetail("patientId", patientId).withDetail("files", count)
                .withDetail("archive", true));
    }

    private static String uniqueEntry(Set<String> used, String entry) {
        String candidate = entry;
        for (int n = 2; !used.add(candidate); n++) {
            candidate = FileNames.withSuffix(entry, n);
        }
        return candidate;
    }

    // ---- helpers ------------------------------------------------------------------------------

    private void requireActive(UUID patientId) {
        if (!access.isActive(patientId)) {
            throw RecordsException.notFound();
        }
    }

    private MedicalFile available(UUID patientId, UUID fileId) {
        return files.findByIdAndPatientId(fileId, patientId)
                .filter(f -> MedicalFile.AVAILABLE.equals(f.getStatus())).orElseThrow(RecordsException::notFound);
    }

    private UUID createFolderRow(UUID patientId, UUID parentId, String name, DataKey key) {
        UUID id = Ids.newId();
        folders.saveAndFlush(new MedicalFolder(id, patientId, parentId, crypto.encryptText(key, folderField(id), name)));
        return id;
    }

    /** Finds or creates the nested folders of an uploaded relative path below {@code baseFolderId}. Call inside the patient lock + tx. */
    private UUID resolveFolderChain(UUID patientId, UUID baseFolderId, List<String> segments, DataKey key) {
        Tree tree = tree(patientId, key);
        if (baseFolderId != null && !tree.contains(baseFolderId)) {
            throw RecordsException.notFound();
        }
        UUID current = baseFolderId;
        int depth = tree.depth(baseFolderId);
        for (String segment : segments) {
            Optional<MedicalFolder> existing = tree.childNamed(current, segment);
            if (existing.isPresent()) {
                current = existing.get().getId();
            } else {
                if (++depth > properties.maxFolderDepth()) {
                    throw RecordsException.badRequest("records.error.depth", properties.maxFolderDepth());
                }
                current = createFolderRow(patientId, current, segment, key);
                tree = tree(patientId, key);
            }
        }
        return current;
    }

    private boolean nameTaken(UUID patientId, UUID folderId, String name, DataKey key, UUID ignoreFileId) {
        String wanted = sortKey(name);
        return files.findInFolder(patientId, folderId, List.of(MedicalFile.AVAILABLE, MedicalFile.PENDING)).stream()
                .filter(f -> !f.getId().equals(ignoreFileId))
                .anyMatch(f -> sortKey(fileName(key, f)).equals(wanted));
    }

    private String uniqueFileName(UUID patientId, UUID folderId, String name, DataKey key, UUID ignoreFileId) {
        Set<String> taken = new HashSet<>();
        files.findInFolder(patientId, folderId, List.of(MedicalFile.AVAILABLE, MedicalFile.PENDING)).stream()
                .filter(f -> !f.getId().equals(ignoreFileId))
                .forEach(f -> taken.add(sortKey(fileName(key, f))));
        if (!taken.contains(sortKey(name))) {
            return name;
        }
        for (int n = 2; ; n++) {
            String candidate = FileNames.withSuffix(name, n);
            if (!taken.contains(sortKey(candidate))) {
                return candidate;
            }
        }
    }

    private String fileName(DataKey key, MedicalFile file) {
        return crypto.decryptText(key, fileField(file.getId()), file.getDisplayNameEnc());
    }

    static String fileField(UUID id) {
        return "file_name:" + id;
    }

    static String folderField(UUID id) {
        return "folder_name:" + id;
    }

    private static String sortKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private String keyIdOf() {
        return "dek";
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void closeQuietly(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignored) {
                // already failing
            }
        }
    }

    private Tree tree(UUID patientId, DataKey key) {
        return new Tree(folders.findByPatientId(patientId), key);
    }

    /** In-memory view of a patient's folder tree with lazily decrypted names. */
    private final class Tree {

        private final Map<UUID, MedicalFolder> byId = new HashMap<>();
        private final Map<UUID, List<MedicalFolder>> children = new HashMap<>();
        private final Map<UUID, String> names = new HashMap<>();
        private final DataKey key;

        Tree(List<MedicalFolder> all, DataKey key) {
            this.key = key;
            for (MedicalFolder f : all) {
                byId.put(f.getId(), f);
                children.computeIfAbsent(f.getParentId(), p -> new ArrayList<>()).add(f);
            }
        }

        boolean contains(UUID id) {
            return byId.containsKey(id);
        }

        Optional<MedicalFolder> get(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        List<MedicalFolder> children(UUID parentId) {
            return children.getOrDefault(parentId, List.of());
        }

        String name(MedicalFolder folder) {
            return names.computeIfAbsent(folder.getId(),
                    id -> crypto.decryptText(key, folderField(id), folder.getNameEnc()));
        }

        Optional<MedicalFolder> childNamed(UUID parentId, String name) {
            String wanted = sortKey(name);
            return children(parentId).stream().filter(c -> sortKey(name(c)).equals(wanted)).findFirst();
        }

        boolean hasChildNamed(UUID parentId, String name, UUID ignoreId) {
            String wanted = sortKey(name);
            return children(parentId).stream().filter(c -> !c.getId().equals(ignoreId))
                    .anyMatch(c -> sortKey(name(c)).equals(wanted));
        }

        /** Number of folders from the root down to this one (root = 0). */
        int depth(UUID folderId) {
            int depth = 0;
            for (UUID id = folderId; id != null && depth <= 1000; depth++) {
                id = byId.get(id).getParentId();
            }
            return depth;
        }

        /** Number of folder levels below this folder (a leaf has height 0). */
        int height(UUID folderId) {
            return children(folderId).stream().mapToInt(c -> 1 + height(c.getId())).max().orElse(0);
        }

        boolean isDescendant(UUID candidate, UUID ancestor) {
            for (UUID id = candidate; id != null; id = byId.get(id).getParentId()) {
                if (id.equals(ancestor)) {
                    return true;
                }
            }
            return false;
        }

        Set<UUID> subtree(UUID root) {
            Set<UUID> result = new LinkedHashSet<>();
            List<UUID> queue = new ArrayList<>(List.of(root));
            while (!queue.isEmpty()) {
                UUID id = queue.remove(queue.size() - 1);
                if (result.add(id)) {
                    children(id).forEach(c -> queue.add(c.getId()));
                }
            }
            return result;
        }

        /** Names from below {@code from} (exclusive; null = root) down to {@code folderId}, joined with "/". */
        String relativePath(UUID folderId, UUID from) {
            List<String> parts = new ArrayList<>();
            for (UUID id = folderId; id != null && !id.equals(from); id = byId.get(id).getParentId()) {
                parts.add(0, name(byId.get(id)));
            }
            return String.join("/", parts);
        }

        List<FolderListing.Crumb> crumbs(UUID folderId) {
            List<FolderListing.Crumb> crumbs = new ArrayList<>();
            for (UUID id = folderId; id != null; id = byId.get(id).getParentId()) {
                crumbs.add(0, new FolderListing.Crumb(id, name(byId.get(id))));
            }
            return crumbs;
        }

        List<FolderListing.FolderOption> options() {
            return byId.values().stream()
                    .map(f -> new FolderListing.FolderOption(f.getId(), path(f.getId())))
                    .sorted(Comparator.comparing(o -> sortKey(o.path())))
                    .limit(MAX_FOLDER_OPTIONS).toList();
        }

        private String path(UUID folderId) {
            return crumbs(folderId).stream().map(FolderListing.Crumb::name).collect(java.util.stream.Collectors.joining(" / "));
        }
    }
}
