package com.ecoloop;

import com.ecoloop.classification.api.ClassificationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassificationResultTest {

    @Test
    void manualReviewUsesDatabaseSupportedDeviceStatus() {
        ClassificationResult result = new ClassificationResult("other", 0.0, "roboflow", "model", "manual_review");

        assertEquals("manual", result.deviceAiStatus());
    }

    @Test
    void completedStatusIsPreservedForDevice() {
        ClassificationResult result = new ClassificationResult("laptop", 0.9, "roboflow", "model", "completed");

        assertEquals("completed", result.deviceAiStatus());
    }

    @Test
    void missingStatusDefaultsToCompletedForDevice() {
        ClassificationResult result = new ClassificationResult("laptop", 0.9, "roboflow", "model", null);

        assertEquals("completed", result.deviceAiStatus());
    }
}
