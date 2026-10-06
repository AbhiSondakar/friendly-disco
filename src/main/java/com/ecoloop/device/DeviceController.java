package com.ecoloop.device;

import com.ecoloop.classification.api.ClassificationApi;
import com.ecoloop.classification.api.ClassificationResult;
import com.ecoloop.common.SessionUser;
import com.ecoloop.common.upload.FileStorageService;
import com.ecoloop.pickup.PickupRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private static final Logger log = LoggerFactory.getLogger(DeviceController.class);

    private final ClassificationApi classificationApi;
    private final DeviceRepository devices;
    private final com.ecoloop.pickup.PickupService pickupService;
    private final FileStorageService fileStorageService;

    public DeviceController(ClassificationApi classificationApi,
                            DeviceRepository devices,
                            com.ecoloop.pickup.PickupService pickupService,
                            FileStorageService fileStorageService) {
        this.classificationApi = classificationApi;
        this.devices = devices;
        this.pickupService = pickupService;
        this.fileStorageService = fileStorageService;
    }

    private UUID user(HttpServletRequest r) {
        return SessionUser.require(r).id();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Device submit(@RequestPart("image") MultipartFile image,
                         @RequestParam(defaultValue = "good") String condition,
                         HttpServletRequest r) throws IOException {
        UUID userId = user(r);
        FileStorageService.StoredFile stored = fileStorageService.storeFile(userId, "devices", image, false);
        String mime = stored.metadata().getContentType();
        String imageUrl = stored.publicUri();

        return createDevice(userId, stored.content(), mime, condition, imageUrl);
    }

    private Device createDevice(UUID userId, byte[] image, String mime, String condition, String imageUrl) {
        Device device = new Device();
        device.setId(UUID.randomUUID());
        device.setUserId(userId);
        device.setCondition(condition != null ? condition.trim() : "good");
        device.setImageUrl(imageUrl);
        device.setCreatedAt(Instant.now());
        device.setUpdatedAt(Instant.now());
        device.setAiStatus("pending");
        device = devices.save(device);

        ClassificationResult result = classificationApi.classify(image, mime, device.getId(), imageUrl);
        device.setCategory(result.category());
        device.setAiCategory(result.category());
        device.setAiConfidence(BigDecimal.valueOf(result.confidence()));
        device.setAiProvider(result.provider());
        device.setAiStatus(result.status() != null ? result.status() : "completed");
        device.setUpdatedAt(Instant.now());
        Device saved = devices.save(device);
        log.info("Device created and classified: id={} user={} category={} confidence={}",
                saved.getId(), userId, result.category(), result.confidence());
        return saved;
    }

    @GetMapping
    public List<Device> list(HttpServletRequest r) {
        return devices.findAllByUserIdOrderByCreatedAtDesc(user(r));
    }

    @GetMapping("/{id}")
    public Device get(@PathVariable UUID id, HttpServletRequest r) {
        return devices.findByIdAndUserId(id, user(r))
            .orElseThrow(() -> new NoSuchElementException("Device not found"));
    }

    @PostMapping("/{id}/cancel-pickup")
    public PickupRequest cancelPickup(@PathVariable UUID id, HttpServletRequest r) {
        UUID userId = user(r);
        Device device = devices.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new NoSuchElementException("Device not found"));
        return pickupService.cancelPickupByDevice(userId, device.getId());
    }
}
