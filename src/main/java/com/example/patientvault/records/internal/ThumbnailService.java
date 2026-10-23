package com.example.patientvault.records.internal;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import net.coobird.thumbnailator.Thumbnails;

/**
 * JPEG thumbnails for photos. Only formats the JVM can decode (JPEG, PNG, TIFF) get one; WebP and HEIC fall back
 * to an icon in the UI. The image size is read from the header first so that a decompression bomb is never decoded.
 */
@Component
class ThumbnailService {

    private static final Logger LOG = LoggerFactory.getLogger(ThumbnailService.class);

    private final UploadProperties properties;

    ThumbnailService(UploadProperties properties) {
        this.properties = properties;
    }

    /** Pass a factory so the source can be read twice (size check, then decode). */
    Optional<byte[]> create(StreamSource source) {
        try {
            if (!withinPixelLimit(source)) {
                return Optional.empty();
            }
            try (InputStream in = source.open()) {
                BufferedImage scaled = Thumbnails.of(in).size(properties.thumbnailSize(), properties.thumbnailSize())
                        .asBufferedImage();
                BufferedImage flat = new BufferedImage(scaled.getWidth(), scaled.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = flat.createGraphics();
                try {
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, flat.getWidth(), flat.getHeight());
                    g.drawImage(scaled, 0, 0, null);
                } finally {
                    g.dispose();
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                if (!ImageIO.write(flat, "jpg", out)) {
                    return Optional.empty();
                }
                return Optional.of(out.toByteArray());
            }
        } catch (IOException | RuntimeException e) {
            LOG.debug("No thumbnail created: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private boolean withinPixelLimit(StreamSource source) throws IOException {
        try (InputStream in = source.open(); ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis == null) {
                return false;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                return (long) reader.getWidth(0) * reader.getHeight(0) <= properties.thumbnailMaxPixels();
            } finally {
                reader.dispose();
            }
        }
    }

    /** A re-openable source of the original (plaintext) bytes. */
    @FunctionalInterface
    interface StreamSource {
        InputStream open() throws IOException;
    }
}
