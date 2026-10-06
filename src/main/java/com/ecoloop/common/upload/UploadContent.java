package com.ecoloop.common.upload;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "upload_contents")
public class UploadContent {

    @Id
    @Column(name = "upload_id")
    private UUID uploadId;

    @Column(name = "content", nullable = false, columnDefinition = "bytea")
    private byte[] content;

    protected UploadContent() {}

    public UploadContent(UUID uploadId, byte[] content) {
        this.uploadId = uploadId;
        this.content = content;
    }

    public UUID getUploadId() { return uploadId; }
    public byte[] getContent() { return content; }
}
