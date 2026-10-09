package com.ecoloop.partner;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partners")
public class Partner {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "org_name", nullable = false, length = 255)
    private String orgName;

    @Column(length = 100)
    private String type;

    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "license_no", length = 255)
    private String licenseNo;

    @Column(name = "service_areas", columnDefinition = "TEXT")
    private String serviceAreas;

    @Column(name = "capabilities", columnDefinition = "TEXT")
    private String capabilities;

    @Column(nullable = false)
    private int capacity = 10;

    @Column(precision = 3, scale = 2)
    private BigDecimal rating = new BigDecimal("5.00");

    @Column(name = "license_upload_id")
    private UUID licenseUploadId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Partner() {}

    public Partner(UUID userId, String orgName, String type, String licenseNo) {
        this.userId = userId;
        this.orgName = orgName;
        this.type = type;
        this.licenseNo = licenseNo;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLicenseNo() { return licenseNo; }
    public void setLicenseNo(String licenseNo) { this.licenseNo = licenseNo; }
    public UUID getLicenseUploadId() { return licenseUploadId; }
    public void setLicenseUploadId(UUID licenseUploadId) { this.licenseUploadId = licenseUploadId; }
    public String getServiceAreas() { return serviceAreas; }
    public void setServiceAreas(String serviceAreas) { this.serviceAreas = serviceAreas; }
    public String getCapabilities() { return capabilities; }
    public void setCapabilities(String capabilities) { this.capabilities = capabilities; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
