package com.ecoloop.audit;

import com.ecoloop.identity.User;
import com.ecoloop.identity.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AuditExportStreamingTest {

    @Autowired
    private AdminAuditController adminAuditController;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void exportAuditCsvStreamsOnBackgroundThreadWithoutSecurityContextHolder() throws Exception {
        // Setup authenticated admin in current thread
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("admin@ecoloop.test", "pass",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        // Seed audit log with formula injection payload and sensitive details
        AuditLog logWithFormula = new AuditLog(
            UUID.randomUUID(), "HOUSEHOLD", "=cmd|' /C calc'!A0", "device", UUID.randomUUID(), "success",
            Map.of("password", "supersecret123", "normalKey", "normalValue")
        );
        auditLogRepository.save(logWithFormula);

        // 1. Controller captures auth and returns StreamingResponseBody
        ResponseEntity<StreamingResponseBody> response = adminAuditController.exportAuditCsv(100, null);
        assertNotNull(response.getBody());

        // 2. Execute StreamingResponseBody on background thread with cleared SecurityContextHolder
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AtomicBoolean ranWithoutAuth = new AtomicBoolean(false);

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            SecurityContextHolder.clearContext();
            assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "Background thread must not have SecurityContextHolder propagated");
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(),
                "Background thread must have no transaction active prior to streaming callback");
            try {
                response.getBody().writeTo(out);
                ranWithoutAuth.set(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        future.get();
        assertTrue(ranWithoutAuth.get(), "Streaming callback must succeed on background thread");

        String csv = out.toString(StandardCharsets.UTF_8);
        assertTrue(csv.contains("id,actor_id,actor_role,action,entity_type,entity_id,result,details,created_at"),
            "CSV header must be present");

        // Verify CSV formula injection escaping
        assertTrue(csv.contains("'=cmd|' /C calc'!A0"),
            "Formula injection string must be escaped with single quote: " + csv);

        // Verify sensitive keys redaction
        assertTrue(csv.contains("[redacted]"), "Sensitive field value must be redacted in details: " + csv);
        assertFalse(csv.contains("supersecret123"), "Plain sensitive password must not leak into CSV: " + csv);
        assertTrue(csv.contains("normalValue"), "Non-sensitive details must be preserved: " + csv);
    }
}
