package com.ecoloop.common.upload;

import com.ecoloop.common.SessionUser;
import com.ecoloop.device.Device;
import com.ecoloop.device.DeviceRepository;
import com.ecoloop.partner.Partner;
import com.ecoloop.partner.PartnerRepository;
import com.ecoloop.pickup.PickupRepository;
import com.ecoloop.pickup.PickupRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final FileStorageService storageService;
    private final UploadMetadataRepository uploadRepository;
    private final DeviceRepository deviceRepository;
    private final PickupRepository pickupRepository;
    private final PartnerRepository partnerRepository;

    public UploadController(FileStorageService storageService,
                            UploadMetadataRepository uploadRepository,
                            DeviceRepository deviceRepository,
                            PickupRepository pickupRepository,
                            PartnerRepository partnerRepository) {
        this.storageService = storageService;
        this.uploadRepository = uploadRepository;
        this.deviceRepository = deviceRepository;
        this.pickupRepository = pickupRepository;
        this.partnerRepository = partnerRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable UUID id) throws IOException {
        SessionUser currentUser = SessionUser.require();
        UploadMetadata metadata = uploadRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload not found"));

        checkAuthorization(currentUser, metadata);
        return storageService.serveUpload(metadata);
    }

    private void checkAuthorization(SessionUser currentUser, UploadMetadata metadata) {
        if ("ADMIN".equalsIgnoreCase(currentUser.role())) {
            return;
        }

        // Owner of the upload always has access
        if (currentUser.id().equals(metadata.getUserId())) {
            return;
        }

        // If it's a partner checking assigned job assets
        if ("PARTNER".equalsIgnoreCase(currentUser.role())) {
            Optional<Partner> partnerOpt = partnerRepository.findByUserId(currentUser.id());
            if (partnerOpt.isPresent() && "approved".equalsIgnoreCase(partnerOpt.get().getStatus())) {
                UUID partnerId = partnerOpt.get().getId();
                // Check if this upload belongs to a device assigned to this partner
                for (PickupRequest pr : pickupRepository.findAllByPartnerId(partnerId)) {
                    if (pr.getDeviceId() != null) {
                        Optional<Device> dev = deviceRepository.findById(pr.getDeviceId());
                        if (dev.isPresent() && uploadUri(metadata.getId()).equals(dev.get().getImageUrl())) {
                            return;
                        }
                    }
                    if (uploadUri(metadata.getId()).equals(pr.getVerificationEvidenceUrl())) {
                        return;
                    }
                }
            }
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access to file denied");
    }

    private String uploadUri(UUID uploadId) {
        return "/api/uploads/" + uploadId;
    }
}
