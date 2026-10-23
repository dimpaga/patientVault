package com.example.patientvault.records.internal;

import java.util.Locale;
import java.util.Set;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

/**
 * Decides what an upload really is, from its content and never from the browser's claims (spec 7.3):
 * Apache Tika sniffs the leading bytes, DICOM is recognised by "DICM" at offset 128 (or the DICOMDIR index
 * file name on otherwise unrecognised binary content), the detected type must be on the allow-list, and
 * when the file name's extension implies a different type the upload is rejected.
 */
@Component
public class UploadPolicy {

    /** Number of leading bytes inspected. */
    public static final int SNIFF_BYTES = 8192;

    public static final String DICOM = "application/dicom";
    private static final String OCTET_STREAM = "application/octet-stream";
    private static final int DICOM_MAGIC_OFFSET = 128;
    private static final Set<String> INLINE_VIEWABLE = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    /** @param category PHOTO | PDF | DICOM | OTHER */
    public record Detected(String mimeType, String category) {
    }

    private final Tika tika = new Tika();
    private final Set<String> allowed;

    UploadPolicy(UploadProperties properties) {
        this.allowed = Set.copyOf(properties.allowedTypes());
    }

    /**
     * @param header the first bytes of the file (at most {@link #SNIFF_BYTES})
     * @return the detected type
     * @throws RecordsException when the type is not allowed or does not match the file extension
     */
    public Detected inspect(byte[] header, String fileName) {
        String content = detectContent(header, fileName);
        if (!allowed.contains(content)) {
            throw RecordsException.badRequest("records.error.type");
        }
        String byName = fileName == null ? OCTET_STREAM : tika.detect(fileName.toLowerCase(Locale.ROOT));
        if (!OCTET_STREAM.equals(byName) && !compatible(content, byName)) {
            throw RecordsException.badRequest("records.error.mismatch");
        }
        return new Detected(content, categoryOf(content));
    }

    public static boolean isViewableInline(String mimeType) {
        return INLINE_VIEWABLE.contains(mimeType);
    }

    private String detectContent(byte[] header, String fileName) {
        if (hasDicomMagic(header)) {
            return DICOM;
        }
        String detected = tika.detect(header);
        if (OCTET_STREAM.equals(detected) && fileName != null && fileName.equalsIgnoreCase("DICOMDIR")) {
            return DICOM;
        }
        return detected;
    }

    private static boolean hasDicomMagic(byte[] header) {
        return header.length >= DICOM_MAGIC_OFFSET + 4 && header[DICOM_MAGIC_OFFSET] == 'D'
                && header[DICOM_MAGIC_OFFSET + 1] == 'I' && header[DICOM_MAGIC_OFFSET + 2] == 'C'
                && header[DICOM_MAGIC_OFFSET + 3] == 'M';
    }

    /** Same type, or the usual aliases (HEIC/HEIF families), or plain text content under a text extension. */
    private static boolean compatible(String content, String byName) {
        if (content.equals(byName)) {
            return true;
        }
        return family(content).equals(family(byName));
    }

    private static String family(String mime) {
        if (mime.startsWith("image/heic") || mime.startsWith("image/heif")) {
            return "image/heif";
        }
        if (mime.equals("image/tiff") || mime.equals("image/tiff-fx")) {
            return "image/tiff";
        }
        return mime;
    }

    private static String categoryOf(String mime) {
        if (mime.startsWith("image/")) {
            return "PHOTO";
        }
        if (mime.equals("application/pdf")) {
            return "PDF";
        }
        return mime.equals(DICOM) ? "DICOM" : "OTHER";
    }
}
