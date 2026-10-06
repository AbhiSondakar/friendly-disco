package com.ecoloop.classification.api;

public interface VisionProvider {
    String name();
    boolean isConfigured();
    ClassificationResult classify(byte[] image, String mime);
}
