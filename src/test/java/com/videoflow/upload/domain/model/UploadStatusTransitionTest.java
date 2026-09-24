package com.videoflow.upload.domain.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UploadStatusTransitionTest {

    @Test
    void createdCanMoveToUploadingCancelledExpired() {
        assertTrue(UploadStatus.CREATED.canTransitionTo(UploadStatus.UPLOADING));
        assertTrue(UploadStatus.CREATED.canTransitionTo(UploadStatus.CANCELLED));
        assertTrue(UploadStatus.CREATED.canTransitionTo(UploadStatus.EXPIRED));
    }

    @Test
    void createdCannotSkipToCompletingOrTerminalStates() {
        assertFalse(UploadStatus.CREATED.canTransitionTo(UploadStatus.COMPLETING));
        assertFalse(UploadStatus.CREATED.canTransitionTo(UploadStatus.COMPLETED));
        assertFalse(UploadStatus.CREATED.canTransitionTo(UploadStatus.READY));
        assertFalse(UploadStatus.CREATED.canTransitionTo(UploadStatus.PROCESSING));
        assertFalse(UploadStatus.CREATED.canTransitionTo(UploadStatus.FAILED));
        assertFalse(UploadStatus.CREATED.canTransitionTo(UploadStatus.CREATED));
    }

    @Test
    void uploadingCanMoveToCompletingOrTerminalStates() {
        assertTrue(UploadStatus.UPLOADING.canTransitionTo(UploadStatus.COMPLETING));
        assertTrue(UploadStatus.UPLOADING.canTransitionTo(UploadStatus.CANCELLED));
        assertTrue(UploadStatus.UPLOADING.canTransitionTo(UploadStatus.EXPIRED));
        assertFalse(UploadStatus.UPLOADING.canTransitionTo(UploadStatus.CREATED));
        assertFalse(UploadStatus.UPLOADING.canTransitionTo(UploadStatus.COMPLETED));
    }

    @Test
    void completingCanOnlyFinishOrFail() {
        assertTrue(UploadStatus.COMPLETING.canTransitionTo(UploadStatus.COMPLETED));
        assertTrue(UploadStatus.COMPLETING.canTransitionTo(UploadStatus.FAILED));
        assertFalse(UploadStatus.COMPLETING.canTransitionTo(UploadStatus.UPLOADING));
        assertFalse(UploadStatus.COMPLETING.canTransitionTo(UploadStatus.CANCELLED));
    }

    @Test
    void completedOnlyMovesToProcessing() {
        assertTrue(UploadStatus.COMPLETED.canTransitionTo(UploadStatus.PROCESSING));
        assertFalse(UploadStatus.COMPLETED.canTransitionTo(UploadStatus.COMPLETED));
        assertFalse(UploadStatus.COMPLETED.canTransitionTo(UploadStatus.CREATED));
        assertFalse(UploadStatus.COMPLETED.canTransitionTo(UploadStatus.EXPIRED));
    }

    @Test
    void processingMovesToReadyOrFailed() {
        assertTrue(UploadStatus.PROCESSING.canTransitionTo(UploadStatus.READY));
        assertTrue(UploadStatus.PROCESSING.canTransitionTo(UploadStatus.FAILED));
        assertFalse(UploadStatus.PROCESSING.canTransitionTo(UploadStatus.COMPLETED));
    }

    @Test
    void terminalStatesCannotTransition() {
        assertFalse(UploadStatus.READY.canTransitionTo(UploadStatus.COMPLETED));
        assertFalse(UploadStatus.CANCELLED.canTransitionTo(UploadStatus.CREATED));
        assertFalse(UploadStatus.EXPIRED.canTransitionTo(UploadStatus.UPLOADING));
        assertFalse(UploadStatus.FAILED.canTransitionTo(UploadStatus.COMPLETED));
    }
}