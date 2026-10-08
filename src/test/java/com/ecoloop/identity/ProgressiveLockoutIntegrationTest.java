package com.ecoloop.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class ProgressiveLockoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder encoder;

    private String testEmail;
    private String correctPassword;

    @BeforeEach
    void setUp() {
        testEmail = "lockout." + UUID.randomUUID() + "@example.com";
        correctPassword = "CorrectP@ssword123!";

        User user = new User(testEmail, encoder.encode(correctPassword), "Lockout Test User", "HOUSEHOLD");
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @Test
    void consecutiveFailedLoginsTriggerLockout() throws Exception {
        AuthController.LoginRequest badReq = new AuthController.LoginRequest(testEmail, "WrongPassword123!");

        // First 4 failures -> 401 Unauthorized
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isUnauthorized());
        }

        // 5th failure -> records 5th failure, returns 401
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badReq)))
            .andExpect(status().isUnauthorized());

        // 6th attempt (even with correct password) -> 429 Too Many Requests due to progressive lockout!
        AuthController.LoginRequest goodReq = new AuthController.LoginRequest(testEmail, correctPassword);
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(goodReq)))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void successfulLoginResetsFailureCounter() throws Exception {
        AuthController.LoginRequest badReq = new AuthController.LoginRequest(testEmail, "WrongPassword123!");

        // 2 failures
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isUnauthorized());
        }

        // Successful login
        AuthController.LoginRequest goodReq = new AuthController.LoginRequest(testEmail, correctPassword);
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(goodReq)))
            .andExpect(status().isOk());
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        AuthController.LoginRequest goodReq = new AuthController.LoginRequest(testEmail, correctPassword);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(goodReq)))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(goodReq)))
            .andExpect(status().isOk());
    }
}
