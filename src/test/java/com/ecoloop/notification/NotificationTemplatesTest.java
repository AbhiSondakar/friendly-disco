package com.ecoloop.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTemplatesTest {

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void allNotificationTypesRenderValidTitleAndBody(NotificationType type) {
        NotificationTemplates.Rendered rendered = NotificationTemplates.render(type, Map.of());
        assertNotNull(rendered, "Rendered output must not be null for " + type);
        assertNotNull(rendered.title(), "Title must not be null for " + type);
        assertFalse(rendered.title().isBlank(), "Title must not be blank for " + type);
        assertNotNull(rendered.body(), "Body must not be null for " + type);
        assertFalse(rendered.body().isBlank(), "Body must not be blank for " + type);
    }

    @Test
    void formatRefShortensOrReturnsFallback() {
        assertEquals("—", NotificationTemplates.formatRef(null));
        UUID id = UUID.randomUUID();
        String expectedPrefix = id.toString().substring(0, 8);
        assertEquals(expectedPrefix, NotificationTemplates.formatRef(id));
    }

    @Test
    void strReturnsFallbackWhenNullOrBlank() {
        assertEquals("default", NotificationTemplates.str(null, "default"));
        assertEquals("default", NotificationTemplates.str("   ", "default"));
        assertEquals("custom", NotificationTemplates.str("custom", "default"));
    }
}
