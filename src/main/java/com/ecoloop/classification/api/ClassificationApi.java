package com.ecoloop.classification.api;

import java.nio.file.Path;
import java.util.UUID;

public interface ClassificationApi {
    ClassificationResult classify(byte[] image, String mime);
    ClassificationResult classify(byte[] image, String mime, UUID deviceId, String imageUrl);
    ClassificationResult classify(Path imagePath, String mime, UUID deviceId, String imageUrl);
}
