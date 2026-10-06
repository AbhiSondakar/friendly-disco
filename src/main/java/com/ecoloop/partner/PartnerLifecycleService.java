package com.ecoloop.partner;

import com.ecoloop.audit.AuditService;
import com.ecoloop.common.SessionUser;
import com.ecoloop.identity.SessionRevocationService;
import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class PartnerLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(PartnerLifecycleService.class);

    private final PartnerRepository partners;
    private final UserRepository users;
    private final SessionRevocationService sessionRevocationService;
    private final AuditService auditService;

    public PartnerLifecycleService(PartnerRepository partners,
                                   UserRepository users,
                                   SessionRevocationService sessionRevocationService,
                                   AuditService auditService) {
        this.partners = partners;
        this.users = users;
        this.sessionRevocationService = sessionRevocationService;
        this.auditService = auditService;
    }

    @Transactional
    public Partner approve(UUID partnerId) {
        Partner partner = partners.findById(partnerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner not found"));

        partner.setStatus("approved");
        partner.setUpdatedAt(Instant.now());
        Partner savedPartner = partners.save(partner);

        User user = users.findById(partner.getUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner user not found"));

        user.setRole(User.Role.PARTNER.name());
        user.setUpdatedAt(Instant.now());
        users.save(user);

        UUID actorId = SessionUser.current().map(SessionUser::id).orElse(null);
        auditService.record(actorId, "ADMIN", "APPROVE_PARTNER", "partner", partnerId, "success",
            Map.of("status", "approved", "userRole", User.Role.PARTNER.name()));
        sessionRevocationService.revokeAllUserSessions(user.getEmail());

        log.info("Partner approved: partnerId={} userId={}", partnerId, user.getId());
        return savedPartner;
    }

    @Transactional
    public Partner reject(UUID partnerId) {
        Partner partner = partners.findById(partnerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner not found"));

        partner.setStatus("rejected");
        partner.setUpdatedAt(Instant.now());
        Partner savedPartner = partners.save(partner);

        User user = users.findById(partner.getUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner user not found"));

        if (User.Role.PARTNER.name().equalsIgnoreCase(user.getRole())) {
            user.setRole(User.Role.HOUSEHOLD.name());
            user.setUpdatedAt(Instant.now());
            users.save(user);
        }

        UUID actorId = SessionUser.current().map(SessionUser::id).orElse(null);
        auditService.record(actorId, "ADMIN", "REJECT_PARTNER", "partner", partnerId, "success",
            Map.of("status", "rejected", "userRole", user.getRole()));
        sessionRevocationService.revokeAllUserSessions(user.getEmail());

        log.info("Partner rejected: partnerId={} userId={}", partnerId, user.getId());
        return savedPartner;
    }

    @Transactional
    public Partner suspend(UUID partnerId) {
        Partner partner = partners.findById(partnerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner not found"));

        partner.setStatus("suspended");
        partner.setUpdatedAt(Instant.now());
        Partner savedPartner = partners.save(partner);

        User user = users.findById(partner.getUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner user not found"));

        if (User.Role.PARTNER.name().equalsIgnoreCase(user.getRole())) {
            user.setRole(User.Role.HOUSEHOLD.name());
            user.setUpdatedAt(Instant.now());
            users.save(user);
        }

        UUID actorId = SessionUser.current().map(SessionUser::id).orElse(null);
        auditService.record(actorId, "ADMIN", "SUSPEND_PARTNER", "partner", partnerId, "success",
            Map.of("status", "suspended", "userRole", user.getRole()));
        sessionRevocationService.revokeAllUserSessions(user.getEmail());

        log.info("Partner suspended: partnerId={} userId={}", partnerId, user.getId());
        return savedPartner;
    }
}
