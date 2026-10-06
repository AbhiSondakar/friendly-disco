package com.ecoloop.common;

import com.ecoloop.identity.ActiveUserAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final ActiveUserAuthenticationFilter activeUserFilter;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:8081}")
    private List<String> allowedOrigins;

    public SecurityConfig(ActiveUserAuthenticationFilter activeUserFilter) {
        this.activeUserFilter = activeUserFilter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName("_csrf");

        return http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler)
                .ignoringRequestMatchers(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password"))
            .addFilterAfter(activeUserFilter, SecurityContextHolderFilter.class)
            .cors(c -> c.configurationSource(corsConfigurationSource()))
            // Browsers only honor HSTS when the request itself is HTTPS. TLS-terminating
            // proxies must therefore forward the original scheme to the application.
            .headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31_536_000)
                    .includeSubDomains(true)))
            .sessionManagement(s -> s
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(f -> f.changeSessionId())
                .maximumSessions(5))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico",
                                 "/error", "/actuator/health",
                                 "/api/auth/login", "/api/auth/register", "/api/auth/csrf",
                                 "/api/auth/forgot-password", "/api/auth/reset-password")
                    .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/partners").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/partners/{id}").authenticated()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/partners/**").hasRole("PARTNER")
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                 "/v3/api-docs", "/v3/api-docs/**",
                                 "/swagger-resources/**", "/webjars/**")
                    .hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l.disable())
            .exceptionHandling(e -> e
                .authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setExposedHeaders(List.of("X-XSRF-TOKEN"));
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", config);
        return src;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration c) throws Exception {
        return c.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
