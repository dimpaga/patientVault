package com.example.patientvault.records.internal;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Sanitising of names that arrive from the browser. Names are stored encrypted and only ever used for display
 * and as the download file name, but they are still cleaned of path parts, control and direction-override
 * characters and bounded in length. Relative paths from folder uploads are validated segment by segment.
 */
final class FileNames {

    static final int MAX_NAME_LENGTH = 200;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private FileNames() {
    }

    /** A single file or folder name. Never empty; "." and ".." become "file". */
    static String sanitize(String raw) {
        String name = raw == null ? "" : raw;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        StringBuilder clean = new StringBuilder(name.length());
        name.codePoints().filter(FileNames::allowed).forEach(clean::appendCodePoint);
        name = WHITESPACE.matcher(clean).replaceAll(" ").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return "file";
        }
        return truncate(name);
    }

    /** Folder name typed by the user: same cleaning, but an empty result is an error rather than "file". */
    static String sanitizeFolderName(String raw) {
        String name = sanitize(raw);
        return raw == null || raw.isBlank() ? "" : name;
    }

    /**
     * Folder segments of {@code webkitRelativePath} (everything except the last segment, which is the file).
     * Rejects absolute paths, "..", empty or control-character segments and paths deeper than {@code maxDepth}.
     */
    static List<String> folderSegments(String relativePath, int maxDepth) {
        if (relativePath == null || relativePath.isBlank()) {
            return List.of();
        }
        if (relativePath.startsWith("/") || relativePath.startsWith("\\") || relativePath.indexOf('\\') >= 0
                || relativePath.length() > 4096) {
            throw RecordsException.badRequest("records.error.path");
        }
        String[] parts = relativePath.split("/", -1);
        List<String> segments = new ArrayList<>();
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (part.isBlank() || part.equals(".") || part.equals("..")
                    || part.codePoints().anyMatch(c -> Character.isISOControl(c))) {
                throw RecordsException.badRequest("records.error.path");
            }
            segments.add(sanitize(part));
        }
        if (segments.size() > maxDepth) {
            throw RecordsException.badRequest("records.error.depth", maxDepth);
        }
        return segments;
    }

    /** "photo.jpg" with suffix 2 -> "photo (2).jpg". */
    static String withSuffix(String name, int number) {
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot >= name.length() - 12) {
            return name.substring(0, dot) + " (" + number + ")" + name.substring(dot);
        }
        return name + " (" + number + ")";
    }

    private static boolean allowed(int codePoint) {
        if (Character.isISOControl(codePoint)) {
            return false;
        }
        int type = Character.getType(codePoint);
        // format characters include the bidi overrides used to disguise extensions
        return type != Character.FORMAT && type != Character.PRIVATE_USE && type != Character.UNASSIGNED
                && type != Character.SURROGATE && codePoint != '<' && codePoint != '>' && codePoint != '"'
                && codePoint != ':' && codePoint != '|' && codePoint != '?' && codePoint != '*';
    }

    private static String truncate(String name) {
        if (name.codePointCount(0, name.length()) <= MAX_NAME_LENGTH) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 && name.length() - dot <= 12 ? name.substring(dot) : "";
        String base = name.substring(0, dot > 0 && !extension.isEmpty() ? dot : name.length());
        int keep = MAX_NAME_LENGTH - extension.codePointCount(0, extension.length());
        int end = base.offsetByCodePoints(0, Math.min(keep, base.codePointCount(0, base.length())));
        return base.substring(0, end) + extension;
    }
}
