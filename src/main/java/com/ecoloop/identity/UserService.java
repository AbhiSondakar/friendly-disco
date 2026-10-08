package com.ecoloop.identity;

import com.ecoloop.partner.PartnerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PushTokenRepository pushTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SessionRevocationService sessionRevocationService;
    private final ObjectProvider<PartnerRepository> partnerRepositoryProvider;

    public UserService(UserRepository userRepository,
                       PushTokenRepository pushTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       SessionRevocationService sessionRevocationService,
                       ObjectProvider<PartnerRepository> partnerRepositoryProvider) {
        this.userRepository = userRepository;
        this.pushTokenRepository = pushTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.sessionRevocationService = sessionRevocationService;
        this.partnerRepositoryProvider = partnerRepositoryProvider;
    }

    @Transactional
    public void softDeleteUser(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.isDeleted()) {
            return;
        }

        // 1. Revoke all active sessions
        sessionRevocationService.revokeAllUserSessions(user.getEmail());

        // 2. Purge push tokens and password reset tokens
        pushTokenRepository.deleteAllByUserId(userId);
        passwordResetTokenRepository.deleteAllByUserId(userId);

        // 3. Soft-delete user entity (anonymizes name, email, phone, address, and sets sentinel password hash)
        user.softDelete();
        userRepository.save(user);

        // 4. Anonymize partner profile fields if user was an organization partner
        partnerRepositoryProvider.ifAvailable(partnerRepo -> {
            partnerRepo.findByUserId(userId).ifPresent(partner -> {
                partner.setOrgName("Anonymized Partner");
                partner.setServiceAreas(null);
                partner.setCapabilities(null);
                partner.setLicenseNo(null);
                partner.setLicenseUploadId(null);
                partner.setStatus("suspended");
                partner.setUpdatedAt(Instant.now());
                partnerRepo.save(partner);
            });
        });

        log.info("User successfully soft-deleted: userId={}", userId);
    }
}
