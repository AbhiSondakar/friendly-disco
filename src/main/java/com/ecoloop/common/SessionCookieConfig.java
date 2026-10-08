package com.ecoloop.common;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@ConditionalOnClass(DefaultCookieSerializer.class)
public class SessionCookieConfig {

    public static final String SESSION_COOKIE_NAME = "__Host-ECOLOOP_SESSION";

    @Value("${server.servlet.session.cookie.secure:${COOKIE_SECURE:true}}")
    private boolean cookieSecure;

    @Value("${server.servlet.session.cookie.same-site:${COOKIE_SAME_SITE:none}}")
    private String cookieSameSite;

    @PostConstruct
    public void validateCookieSecurity() {
        validateSameSiteAndSecure(cookieSameSite, cookieSecure);
    }

    public static void validateSameSiteAndSecure(String sameSite, boolean secure) {
        if ("none".equalsIgnoreCase(sameSite) && !secure) {
            throw new IllegalStateException("Security violation: Session cookie SameSite=None requires Secure=true");
        }
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        validateCookieSecurity();
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(SESSION_COOKIE_NAME);
        serializer.setCookiePath("/");
        serializer.setUseSecureCookie(cookieSecure);
        serializer.setSameSite(cookieSameSite);
        serializer.setUseHttpOnlyCookie(true);
        return serializer;
    }
}
