package com.ecoloop.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SessionRevocationService {

    private static final Logger log = LoggerFactory.getLogger(SessionRevocationService.class);

    private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessionRepositoryProvider;

    public SessionRevocationService(ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessionRepositoryProvider) {
        this.sessionRepositoryProvider = sessionRepositoryProvider;
    }

    public void revokeAllUserSessions(String principalName) {
        if (principalName == null || principalName.isBlank()) {
            return;
        }

        FindByIndexNameSessionRepository<? extends Session> repository = sessionRepositoryProvider.getIfAvailable();
        if (repository == null) {
            log.debug("Session repository not available for session revocation: principal={}", principalName);
            return;
        }

        try {
            String normalizedPrincipal = User.normalizeEmail(principalName);
            Map<String, ? extends Session> sessions = repository.findByPrincipalName(normalizedPrincipal);
            if (sessions != null && !sessions.isEmpty()) {
                log.info("Revoking {} active sessions for user: {}", sessions.size(), normalizedPrincipal);
                for (String sessionId : sessions.keySet()) {
                    repository.deleteById(sessionId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to revoke sessions for user {}: {}", principalName, e.getMessage());
        }
    }

    public void revokeOtherUserSessions(String principalName, String currentSessionId) {
        if (principalName == null || principalName.isBlank()) {
            return;
        }

        FindByIndexNameSessionRepository<? extends Session> repository = sessionRepositoryProvider.getIfAvailable();
        if (repository == null) {
            log.debug("Session repository not available for session revocation: principal={}", principalName);
            return;
        }

        try {
            String normalizedPrincipal = User.normalizeEmail(principalName);
            Map<String, ? extends Session> sessions = repository.findByPrincipalName(normalizedPrincipal);
            if (sessions != null && !sessions.isEmpty()) {
                int revokedCount = 0;
                for (String sessionId : sessions.keySet()) {
                    if (currentSessionId == null || !sessionId.equals(currentSessionId)) {
                        repository.deleteById(sessionId);
                        revokedCount++;
                    }
                }
                log.info("Revoked {} other active sessions for user: {}", revokedCount, normalizedPrincipal);
            }
        } catch (Exception e) {
            log.warn("Failed to revoke other sessions for user {}: {}", principalName, e.getMessage());
        }
    }
}
