package com.ecoloop.common;

import com.ecoloop.audit.AuditLogDto;
import com.ecoloop.device.DeviceDto;
import com.ecoloop.notification.NotificationDto;
import com.ecoloop.rewards.RewardCatalogItemDto;
import com.ecoloop.rewards.RewardLedgerDto;
import com.ecoloop.rewards.RewardRedemptionDto;
import com.ecoloop.routing.RoutingOfferDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InstantIso8601SerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void instantFieldsSerializeToIso8601WithZ() throws Exception {
        Instant now = Instant.parse("2026-10-07T17:30:00.000Z");

        DeviceDto deviceDto = new DeviceDto(UUID.randomUUID(), UUID.randomUUID(), "laptop", "good",
                "http://img", "laptop", BigDecimal.valueOf(0.9), "completed", "stub", now, now);
        String deviceJson = objectMapper.writeValueAsString(deviceDto);
        assertTrue(deviceJson.contains("\"2026-10-07T17:30:00Z\""), "DeviceDto instant must serialize with trailing Z: " + deviceJson);

        NotificationDto notificationDto = new NotificationDto(UUID.randomUUID(), UUID.randomUUID(), "Title", "Body", false, "type", null, now);
        String notifJson = objectMapper.writeValueAsString(notificationDto);
        assertTrue(notifJson.contains("\"2026-10-07T17:30:00Z\""), "NotificationDto instant must serialize with trailing Z: " + notifJson);

        RoutingOfferDto offerDto = new RoutingOfferDto(UUID.randomUUID(), UUID.randomUUID(), "offered", now, 1, null, now);
        String offerJson = objectMapper.writeValueAsString(offerDto);
        assertTrue(offerJson.contains("\"2026-10-07T17:30:00Z\""), "RoutingOfferDto instant must serialize with trailing Z: " + offerJson);

        RewardLedgerDto ledgerDto = new RewardLedgerDto(UUID.randomUUID(), UUID.randomUUID(), 25, "earn", "desc", null, now);
        String ledgerJson = objectMapper.writeValueAsString(ledgerDto);
        assertTrue(ledgerJson.contains("\"2026-10-07T17:30:00Z\""), "RewardLedgerDto instant must serialize with trailing Z: " + ledgerJson);

        RewardCatalogItemDto catalogDto = new RewardCatalogItemDto(UUID.randomUUID(), "Item", "desc", 100, "http://img", true, now);
        String catJson = objectMapper.writeValueAsString(catalogDto);
        assertTrue(catJson.contains("\"2026-10-07T17:30:00Z\""), "RewardCatalogItemDto instant must serialize with trailing Z: " + catJson);

        RewardRedemptionDto redemptionDto = new RewardRedemptionDto(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100, "completed", now);
        String redJson = objectMapper.writeValueAsString(redemptionDto);
        assertTrue(redJson.contains("\"2026-10-07T17:30:00Z\""), "RewardRedemptionDto instant must serialize with trailing Z: " + redJson);

        AuditLogDto auditDto = new AuditLogDto(UUID.randomUUID(), UUID.randomUUID(), "ADMIN", "act", "ent", UUID.randomUUID(), "OK", null, now);
        String auditJson = objectMapper.writeValueAsString(auditDto);
        assertTrue(auditJson.contains("\"2026-10-07T17:30:00Z\""), "AuditLogDto instant must serialize with trailing Z: " + auditJson);
    }
}
