package com.example.patientvault.records.web;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.example.patientvault.records.blob.BlobStoreException;
import com.example.patientvault.records.internal.FileIntegrityException;
import com.example.patientvault.records.internal.RecordsException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Maps records failures that escape the controllers (for example while another module renders the file panel)
 * to normal error pages, and answers an oversized upload with a readable message instead of a reset.
 * Generic text only: nothing about the file or the patient is revealed.
 */
@ControllerAdvice
@Order(10)
class RecordsExceptionAdvice {

    private final MessageSource messages;

    RecordsExceptionAdvice(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(RecordsException.class)
    void records(RecordsException e, HttpServletResponse response) throws IOException {
        response.sendError(e.status());
    }

    @ExceptionHandler(FileIntegrityException.class)
    void integrity(HttpServletResponse response) throws IOException {
        response.sendError(500);
    }

    @ExceptionHandler(BlobStoreException.class)
    void store(HttpServletResponse response) throws IOException {
        response.sendError(503);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooLarge(HttpServletRequest request) {
        Locale locale = LocaleContextHolder.getLocale();
        String message = messages.getMessage("records.error.tooLarge", new Object[] {"100"},
                "records.error.tooLarge", locale);
        return ResponseEntity.status(413).contentType(MediaType.APPLICATION_JSON).body(Map.of("error", message));
    }
}
