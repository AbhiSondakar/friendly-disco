package com.ecoloop.classification.internal;

import com.ecoloop.classification.api.ClassificationResult;
import com.ecoloop.classification.api.VisionProvider;
import org.springframework.stereotype.Component;

@Component
public class StubVisionProvider implements VisionProvider {

    @Override
    public String name() {
        return "stub";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public ClassificationResult classify(byte[] image, String mime) {
        return new ClassificationResult("other", 0.0, "stub", "stub-model");
    }
}
