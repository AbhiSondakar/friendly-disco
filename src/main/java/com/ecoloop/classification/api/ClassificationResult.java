package com.ecoloop.classification.api;

public record ClassificationResult(
    String category,
    double confidence,
    String provider,
    String model,
    String status
) {
    public ClassificationResult(String category, double confidence, String provider, String model) {
        this(category, confidence, provider, model, "completed");
    }

    public boolean isSuccessful() {
        return "completed".equalsIgnoreCase(status);
    }
}
