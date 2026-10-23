package com.example.patientvault.records;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One folder level of a patient's medical data, ready to display. Names are decrypted; {@code toString()}
 * prints counts only. {@code folderId} is null for the root.
 */
public record FolderListing(
        UUID patientId,
        UUID folderId,
        List<Crumb> crumbs,
        List<FolderItem> folders,
        List<FileItem> files,
        List<FolderOption> folderOptions) {

    public record Crumb(UUID id, String name) {
        @Override
        public String toString() {
            return "Crumb[id=" + id + "]";
        }
    }

    /** A subfolder with the number of folders and files directly inside it. */
    public record FolderItem(UUID id, String name, int itemCount) {
        @Override
        public String toString() {
            return "FolderItem[id=" + id + "]";
        }
    }

    public record FileItem(UUID id, String name, String category, String mimeType, long sizeBytes,
                           boolean hasThumbnail, boolean viewableInline, Instant uploadedAt) {
        @Override
        public String toString() {
            return "FileItem[id=" + id + "]";
        }
    }

    /** Target choice for "move": a folder with its full path, e.g. {@code Study / Series 1}. */
    public record FolderOption(UUID id, String path) {
        @Override
        public String toString() {
            return "FolderOption[id=" + id + "]";
        }
    }

    @Override
    public String toString() {
        return "FolderListing[patient=" + patientId + ", folders=" + folders.size() + ", files=" + files.size() + "]";
    }
}
