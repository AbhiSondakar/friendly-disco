package com.ecoloop.device;

import com.ecoloop.classification.api.ClassificationApi;
import com.ecoloop.classification.api.ClassificationResult;
import com.ecoloop.common.security.ActorContext;
import com.ecoloop.common.SessionUser;
import com.ecoloop.common.upload.FileStorageService;
import com.ecoloop.pickup.PickupWithDevice;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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
    public DeviceDto submit(@RequestPart("image") MultipartFile image,
                            @RequestParam(defaultValue = "good") String condition,
                            HttpServletRequest r) throws IOException {
        UUID userId = user(r);
        FileStorageService.StoredFile stored = fileStorageService.storeFile(userId, "devices", image, false);
        String mime = stored.metadata().getContentType();
        String imageUrl = stored.publicUri();

        Device created = createDevice(userId, stored.content(), mime, condition, imageUrl);
        return DeviceDto.from(created);
    }

    private Device createDevice(UUID userId, byte[] image, String mime, String condition, String imageUrl) {
        UUID deviceId = UUID.randomUUID();

        ClassificationResult result = classificationApi.classify(image, mime, deviceId, imageUrl);

        if ("failed".equals(result.status())) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI classification failed due to a backend issue. Please try again later."
            );
        }

        if (result.confidence() == 0.0 && !result.requiresManualReview()) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "AI model could not classify the image. Please provide a clearer picture."
            );
        }

        Device device = new Device();
        device.setId(deviceId);
        device.setUserId(userId);
        device.setCondition(condition != null ? condition.trim() : "good");
        device.setImageUrl(imageUrl);
        device.setCreatedAt(Instant.now());
        device.setCategory(result.category());
        device.setAiCategory(result.category());
        device.setAiConfidence(BigDecimal.valueOf(result.confidence()));
        device.setAiProvider(result.provider());
        device.setAiStatus(result.deviceAiStatus());
        device.setUpdatedAt(Instant.now());
        
        Device saved = devices.save(device);
        log.info("Device created and classified: id={} user={} category={} confidence={}",
                saved.getId(), userId, result.category(), result.confidence());
        return saved;
    }

    @GetMapping
    public List<DeviceDto> list(HttpServletRequest r) {
        return devices.findAllByUserIdOrderByCreatedAtDesc(user(r)).stream()
            .map(DeviceDto::from)
            .toList();
    }

    @GetMapping("/{id}")
    public DeviceDto get(@PathVariable UUID id, HttpServletRequest r) {
        return devices.findByIdAndUserId(id, user(r))
            .map(DeviceDto::from)
            .orElseThrow(() -> new NoSuchElementException("Device not found"));
    }

    @PostMapping("/{id}/cancel-pickup")
    public PickupWithDevice cancelPickup(@PathVariable UUID id, ActorContext actor) {
        Device device = devices.findByIdAndUserId(id, actor.userId())
            .orElseThrow(() -> new NoSuchElementException("Device not found"));
        return pickupService.enrich(pickupService.cancelOwnedPickupByDevice(actor, device.getId()));
    }
}
