package com.ecoloop.common;

import com.ecoloop.identity.User;
import com.ecoloop.identity.UserPrincipal;
import com.ecoloop.identity.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class SwaggerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User adminUser;
    private User householdUser;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.save(new User(
            "admin." + UUID.randomUUID() + "@example.com", "hash", "Admin User", "ADMIN"
        ));
        householdUser = userRepository.save(new User(
            "household." + UUID.randomUUID() + "@example.com", "hash", "Household User", "HOUSEHOLD"
        ));
    }

    @Test
    void anonymousGets401ForSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousGets401ForApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v3/api-docs.yaml"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void householdGets403ForSwaggerUiAndDocs() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")
                .with(user(UserPrincipal.from(householdUser))))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/v3/api-docs")
                .with(user(UserPrincipal.from(householdUser))))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/v3/api-docs.yaml")
                .with(user(UserPrincipal.from(householdUser))))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminGets200Or302ForSwaggerAndDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                .with(user(UserPrincipal.from(adminUser))))
            .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs.yaml")
                .with(user(UserPrincipal.from(adminUser))))
            .andExpect(status().isOk());
    }
}
