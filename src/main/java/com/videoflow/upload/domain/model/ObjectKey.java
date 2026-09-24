package com.videoflow.upload.domain.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

/**
 * Builds the storage key of an upload.
 *
 * <p>The key keeps the name the user recognises, under a per-upload prefix:
 * {@code uploads/{uploadId}/{safe-name}}. The upload id makes collisions
 * impossible, so the name never has to be mangled for uniqueness, only for
 * safety.
 *
 * <p>A file name arrives from a browser and is therefore hostile input. It may
 * contain path separators, control characters, or be absurdly long. Everything
 * that could change the meaning of the key is removed here rather than trusted
 * to the storage layer.
 */
public final class ObjectKey {

    private static final String PREFIX = "uploads/";
    private static final String FALLBACK_NAME = "file";
    private static final int MAX_NAME_LENGTH = 120;

    private ObjectKey() {
    }

    public static String forUpload(UUID uploadId, String fileName) {
        if (uploadId == null) {
            throw new IllegalArgumentException("uploadId must not be null");
        }
        return PREFIX + uploadId + "/" + safeName(fileName);
    }

    /**
     * Reduces a user-supplied file name to something that cannot escape its
     * prefix or confuse a storage backend, while staying recognisable.
     */
    public static String safeName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return FALLBACK_NAME;
        }

        // Keep only the last segment: "../../etc/passwd" and "C:\tmp\a.mp4"
        // both collapse to their file name.
        String name = fileName.replace('\\', '/');
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }

        // Strip accents so the key stays ASCII without losing the word.
        name = Normalizer.normalize(name, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "");

        name = name.replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[.-]+", "")
                .replaceAll("[.-]+$", "");

        if (name.isBlank()) {
            return FALLBACK_NAME;
        }
        return truncateKeepingExtension(name).toLowerCase(Locale.ROOT);
    }

    /** Long names are cut from the stem so the extension survives. */
    private static String truncateKeepingExtension(String name) {
        if (name.length() <= MAX_NAME_LENGTH) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || name.length() - dot > 12) {
            return name.substring(0, MAX_NAME_LENGTH);
        }
        String extension = name.substring(dot);
        return name.substring(0, MAX_NAME_LENGTH - extension.length()) + extension;
    }
}
