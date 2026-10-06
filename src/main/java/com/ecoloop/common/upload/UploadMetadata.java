package com.ecoloop.common.upload;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "uploads")
public class UploadMetadata {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String purpose;

    @Column(name = "storage_path", nullable = false, length = 1024)
    private String storagePath;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "sha256_checksum", length = 64)
    private String sha256Checksum;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UploadMetadata() {}

    public UploadMetadata(UUID userId, String purpose, String storagePath,
                          String originalFilename, String contentType,
                          long fileSize, String sha256Checksum) {
        this.userId = userId;
        this.purpose = purpose;
        this.storagePath = storagePath;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.sha256Checksum = sha256Checksum;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getSha256Checksum() { return sha256Checksum; }
    public void setSha256Checksum(String sha256Checksum) { this.sha256Checksum = sha256Checksum; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
