package com.example.patientvault.records.internal;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Upload limits and the allow-list of detected content types (spec 7.3).
 *
 * @param maxFileSize        per-file limit
 * @param allowedTypes       detected MIME types that may be stored
 * @param maxFolderDepth     deepest folder nesting accepted from folder uploads
 * @param thumbnailSize      longest edge of generated thumbnails in pixels
 * @param thumbnailMaxPixels largest image (width x height) that is decoded for a thumbnail
 */
@ConfigurationProperties(prefix = "app.uploads")
public record UploadProperties(
        @DefaultValue("100MB") DataSize maxFileSize,
        @DefaultValue({"image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "image/tiff",
                "application/pdf", "application/dicom", "application/zip", "text/plain"}) List<String> allowedTypes,
        @DefaultValue("12") int maxFolderDepth,
        @DefaultValue("320") int thumbnailSize,
        @DefaultValue("25000000") long thumbnailMaxPixels) {
}
