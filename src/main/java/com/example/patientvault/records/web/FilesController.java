package com.example.patientvault.records.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.patientvault.records.blob.BlobStoreException;
import com.example.patientvault.records.internal.FileContent;
import com.example.patientvault.records.internal.FileIntegrityException;
import com.example.patientvault.records.internal.RecordsException;
import com.example.patientvault.records.internal.RecordsService;
import com.example.patientvault.records.internal.UploadSource;

import jakarta.servlet.http.HttpServletResponse;

/**
 * File and folder endpoints of a patient (spec 8.3). Downloads are streamed through the application after the
 * authentication and authorisation checks in {@link RecordsService}; there are no public or pre-signed URLs.
 * Mutations answer htmx requests with an {@code HX-Location} (reload of the file panel) plus a toast, and plain
 * form posts with a redirect and a flash message. File names never appear in URLs, logs or messages.
 */
@Controller
@RequestMapping("/patients/{patientId}/files")
class FilesController {

    private static final Logger LOG = LoggerFactory.getLogger(FilesController.class);

    private final RecordsService records;
    private final MessageSource messages;

    FilesController(RecordsService records, MessageSource messages) {
        this.records = records;
        this.messages = messages;
    }

    // ---- upload (one file per request, called by the browser's upload script) ----------------

    @PostMapping("/upload")
    ResponseEntity<Map<String, Object>> upload(@PathVariable UUID patientId,
                                               @RequestParam("file") MultipartFile file,
                                               @RequestParam(name = "folderId", required = false) UUID folderId,
                                               @RequestParam(name = "relativePath", required = false) String relativePath) {
        UploadSource source = new UploadSource() {
            @Override
            public String originalName() {
                return file.getOriginalFilename();
            }

            @Override
            public long size() {
                return file.getSize();
            }

            @Override
            public InputStream open() throws IOException {
                return file.getInputStream();
            }
        };
        try {
            RecordsService.UploadResult result = records.upload(patientId, folderId, relativePath, source);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", result.fileId());
            body.put("thumbnail", result.hasThumbnail());
            return ResponseEntity.status(201).body(body);
        } catch (RecordsException e) {
            return error(e.status(), text(e.messageKey(), e.messageArg()));
        } catch (BlobStoreException e) {
            LOG.error("Object store failure during upload");
            return error(503, text("records.error.unavailable", null));
        }
    }

    // ---- content -------------------------------------------------------------------------------

    @GetMapping("/{fileId}/content")
    void content(@PathVariable UUID patientId, @PathVariable UUID fileId,
                 @RequestParam(name = "download", defaultValue = "false") boolean download,
                 HttpServletResponse response) throws IOException {
        try (FileContent content = records.open(patientId, fileId, download)) {
            boolean inline = content.inline();
            response.setContentType(inline ? content.mimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                    .filename(content.name(), StandardCharsets.UTF_8).build().toString());
            if (inline && "application/pdf".equals(content.mimeType())) {
                response.setHeader("Content-Security-Policy", "sandbox");
            }
            response.setContentLengthLong(content.size());
            content.transferTo(response.getOutputStream());
        } catch (FileIntegrityException e) {
            if (!response.isCommitted()) {
                response.reset();
                response.sendError(500);
            } else {
                throw e;
            }
        } catch (RecordsException e) {
            response.sendError(e.status());
        } catch (BlobStoreException e) {
            LOG.error("Object store failure while reading a file");
            response.sendError(503);
        }
    }

    @GetMapping("/{fileId}/thumbnail")
    ResponseEntity<byte[]> thumbnail(@PathVariable UUID patientId, @PathVariable UUID fileId) {
        try {
            return records.thumbnail(patientId, fileId)
                    .map(bytes -> ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).contentLength(bytes.length).body(bytes))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (RecordsException e) {
            return ResponseEntity.status(e.status()).build();
        } catch (FileIntegrityException e) {
            return ResponseEntity.status(500).build();
        } catch (BlobStoreException e) {
            return ResponseEntity.status(503).build();
        }
    }

    @GetMapping("/zip")
    void zip(@PathVariable UUID patientId, @RequestParam(name = "folder", required = false) UUID folderId,
             HttpServletResponse response) throws IOException {
        try {
            // the ZIP is built while streaming, so errors found before the first entry can still become an error page
            response.setContentType("application/zip");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename("medical-files.zip", StandardCharsets.UTF_8).build().toString());
            records.zip(patientId, folderId, response.getOutputStream());
        } catch (RecordsException e) {
            if (!response.isCommitted()) {
                response.reset();
                response.sendError(e.status());
            }
        } catch (BlobStoreException e) {
            LOG.error("Object store failure while building an archive");
            if (!response.isCommitted()) {
                response.reset();
                response.sendError(503);
            }
        } catch (FileIntegrityException e) {
            if (!response.isCommitted()) {
                response.reset();
                response.sendError(500);
            } else {
                throw e;
            }
        }
    }

    // ---- files: rename, move, delete ----------------------------------------------------------

    @PostMapping("/{fileId}/rename")
    String renameFile(@PathVariable UUID patientId, @PathVariable UUID fileId, @RequestParam("name") String name,
                      @RequestParam(name = "folder", required = false) UUID folder,
                      @RequestParam(name = "view", required = false) String view,
                      @RequestHeader(name = "HX-Request", required = false) String htmx,
                      HttpServletResponse response, RedirectAttributes redirect) {
        return mutate(patientId, folder, view, htmx, response, redirect, "records.renamed",
                () -> records.renameFile(patientId, fileId, name));
    }

    @PostMapping("/{fileId}/move")
    String moveFile(@PathVariable UUID patientId, @PathVariable UUID fileId,
                    @RequestParam(name = "target", required = false) UUID target,
                    @RequestParam(name = "folder", required = false) UUID folder,
                    @RequestParam(name = "view", required = false) String view,
                    @RequestHeader(name = "HX-Request", required = false) String htmx,
                    HttpServletResponse response, RedirectAttributes redirect) {
        return mutate(patientId, folder, view, htmx, response, redirect, "records.moved",
                () -> records.moveFile(patientId, fileId, target));
    }

    @PostMapping("/{fileId}/delete")
    String deleteFile(@PathVariable UUID patientId, @PathVariable UUID fileId,
                      @RequestParam(name = "folder", required = false) UUID folder,
                      @RequestParam(name = "view", required = false) String view,
                      @RequestHeader(name = "HX-Request", required = false) String htmx,
                      HttpServletResponse response, RedirectAttributes redirect) {
        return mutate(patientId, folder, view, htmx, response, redirect, "records.deleted",
                () -> records.deleteFile(patientId, fileId));
    }

    // ---- folders ------------------------------------------------------------------------------

    @PostMapping("/folders")
    String createFolder(@PathVariable UUID patientId, @RequestParam("name") String name,
                        @RequestParam(name = "folder", required = false) UUID folder,
                        @RequestParam(name = "view", required = false) String view,
                        @RequestHeader(name = "HX-Request", required = false) String htmx,
                        HttpServletResponse response, RedirectAttributes redirect) {
        return mutate(patientId, folder, view, htmx, response, redirect, "records.folder.created",
                () -> records.createFolder(patientId, folder, name));
    }

    @PostMapping("/folders/{folderId}/rename")
    String renameFolder(@PathVariable UUID patientId, @PathVariable UUID folderId, @RequestParam("name") String name,
                        @RequestParam(name = "folder", required = false) UUID folder,
                        @RequestParam(name = "view", required = false) String view,
                        @RequestHeader(name = "HX-Request", required = false) String htmx,
                        HttpServletResponse response, RedirectAttributes redirect) {
        return mutate(patientId, folder, view, htmx, response, redirect, "records.renamed",
                () -> records.renameFolder(patientId, folderId, name));
    }

    @PostMapping("/folders/{folderId}/move")
    String moveFolder(@PathVariable UUID patientId, @PathVariable UUID folderId,
                      @RequestParam(name = "target", required = false) UUID target,
                      @RequestParam(name = "folder", required = false) UUID folder,
                      @RequestParam(name = "view", required = false) String view,
                      @RequestHeader(name = "HX-Request", required = false) String htmx,
                      HttpServletResponse response, RedirectAttributes redirect) {
        return mutate(patientId, folder, view, htmx, response, redirect, "records.moved",
                () -> records.moveFolder(patientId, folderId, target));
    }

    @PostMapping("/folders/{folderId}/delete")
    String deleteFolder(@PathVariable UUID patientId, @PathVariable UUID folderId,
                        @RequestParam(name = "folder", required = false) UUID folder,
                        @RequestParam(name = "view", required = false) String view,
                        @RequestHeader(name = "HX-Request", required = false) String htmx,
                        HttpServletResponse response, RedirectAttributes redirect) {
        // deleting the folder the user is standing in sends them to the parent level (the root is always safe)
        UUID back = folderId.equals(folder) ? null : folder;
        return mutate(patientId, back, view, htmx, response, redirect, "records.deleted",
                () -> records.deleteFolder(patientId, folderId));
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** Runs one change and answers with a refresh of the file panel (htmx) or a redirect (plain form). */
    private String mutate(UUID patientId, UUID folder, String view, String htmx, HttpServletResponse response,
                          RedirectAttributes redirect, String successKey, Runnable action) {
        String message;
        boolean ok = true;
        try {
            action.run();
            message = text(successKey, null);
        } catch (RecordsException e) {
            ok = false;
            message = text(e.messageKey(), e.messageArg());
        } catch (BlobStoreException e) {
            LOG.error("Object store failure during a file change");
            ok = false;
            message = text("records.error.unavailable", null);
        }
        String url = filesUrl(patientId, folder, view);
        if ("true".equalsIgnoreCase(htmx)) {
            response.setHeader("HX-Trigger", toast(message, ok ? "success" : "danger"));
            if (ok) {
                response.setHeader("HX-Location", "{\"path\":\"" + url + "\",\"target\":\"#pv-tabs\",\"swap\":\"outerHTML\"}");
            } else {
                response.setHeader("HX-Reswap", "none");
            }
            return "fragments/empty :: none";
        }
        redirect.addFlashAttribute(ok ? "flashMessage" : "flashError", message);
        return "redirect:" + url;
    }

    static String filesUrl(UUID patientId, UUID folder, String view) {
        StringBuilder url = new StringBuilder("/patients/").append(patientId).append("/files");
        char sep = '?';
        if (folder != null) {
            url.append(sep).append("folder=").append(folder);
            sep = '&';
        }
        if ("list".equals(view)) {
            url.append(sep).append("view=list");
        }
        return url.toString();
    }

    private ResponseEntity<Map<String, Object>> error(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }

    private String text(String key, Object arg) {
        Locale locale = LocaleContextHolder.getLocale();
        return messages.getMessage(key, arg == null ? null : new Object[] {arg}, key, locale);
    }

    /** {@code HX-Trigger} header value for the toast handler in app.js; non-ASCII is escaped because header values are ASCII. */
    static String toast(String message, String level) {
        StringBuilder out = new StringBuilder("{\"showToast\":{\"message\":\"");
        for (char c : message.toCharArray()) {
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20 || c > 0x7e) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.append("\",\"level\":\"").append(level).append("\"}}").toString();
    }
}
