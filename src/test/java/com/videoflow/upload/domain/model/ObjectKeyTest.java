package com.videoflow.upload.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObjectKeyTest {

    private static final UUID UPLOAD_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void keepsTheRecognisableNameUnderThePerUploadPrefix() {
        assertEquals(
                "uploads/11111111-2222-3333-4444-555555555555/vacaciones-2026.mp4",
                ObjectKey.forUpload(UPLOAD_ID, "Vacaciones 2026.mp4"));
    }

    @Test
    void collapsesPathTraversalToTheFileNameAlone() {
        assertEquals("passwd", ObjectKey.safeName("../../etc/passwd"));
        assertEquals("a.mp4", ObjectKey.safeName("C:\\Users\\me\\a.mp4"));
        assertEquals("b.mp4", ObjectKey.safeName("/absolute/path/b.mp4"));
    }

    @Test
    void stripsAccentsAndControlCharactersWithoutLosingTheWord() {
        assertEquals("cancion-nina.mp3", ObjectKey.safeName("Canción Niña.mp3"));
        assertEquals("clip.mp4", ObjectKey.safeName("clip\u0000\u0007.mp4"));
    }

    @Test
    void collapsesRunsOfUnsafeCharactersAndTrimsTheEdges() {
        assertEquals("a-b.mp4", ObjectKey.safeName("a   ***   b.mp4"));
        assertEquals("video.mp4", ObjectKey.safeName("...video.mp4..."));
    }

    @Test
    void fallsBackWhenNothingUsableSurvives() {
        assertEquals("file", ObjectKey.safeName(null));
        assertEquals("file", ObjectKey.safeName("   "));
        assertEquals("file", ObjectKey.safeName("///"));
    }

    @Test
    void truncatesLongNamesButKeepsTheExtension() {
        String name = "x".repeat(400) + ".mkv";

        String safe = ObjectKey.safeName(name);

        assertTrue(safe.endsWith(".mkv"), safe);
        assertTrue(safe.length() <= 120, "length was " + safe.length());
    }

    @Test
    void acceptsNamesWithoutExtension() {
        assertEquals("readme", ObjectKey.safeName("README"));
    }
}
