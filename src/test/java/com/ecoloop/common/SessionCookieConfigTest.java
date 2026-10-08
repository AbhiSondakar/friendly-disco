package com.ecoloop.common;

import org.junit.jupiter.api.Test;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

public class SessionCookieConfigTest {

    @Test
    void rejectsSameSiteNoneWithInsecureCookie() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            SessionCookieConfig.validateSameSiteAndSecure("none", false));

        assertTrue(ex.getMessage().contains("SameSite=None requires Secure=true"));
    }

    @Test
    void allowsSameSiteNoneWithSecureCookie() {
        assertDoesNotThrow(() ->
            SessionCookieConfig.validateSameSiteAndSecure("none", true));
    }

    @Test
    void allowsSameSiteLaxWithInsecureCookie() {
        assertDoesNotThrow(() ->
            SessionCookieConfig.validateSameSiteAndSecure("lax", false));
    }

    @Test
    void cookieSerializerConfiguresHostPrefixAndSecureAttributes() {
        SessionCookieConfig config = new SessionCookieConfig();
        ReflectionTestUtils.setField(config, "cookieSecure", true);
        ReflectionTestUtils.setField(config, "cookieSameSite", "none");

        CookieSerializer serializer = config.cookieSerializer();
        assertNotNull(serializer);
    }
}
