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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaginationSafetyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User adminUser;
    private User householdUser;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.save(new User(
            "admin." + UUID.randomUUID() + "@ecoloop.test", "hash", "Admin", "ADMIN"
        ));
        householdUser = userRepository.save(new User(
            "household." + UUID.randomUUID() + "@ecoloop.test", "hash", "Household", "HOUSEHOLD"
        ));
    }

    private RequestPostProcessor asUser(User u) {
        return user(new UserPrincipal(u.getId(), u.getEmail(), u.getPasswordHash(), u.getRole(), true));
    }

    @Test
    void endpointsRejectSizeGreaterThan200With400() throws Exception {
        // Test household endpoints
        mockMvc.perform(get("/api/notifications?size=201").with(asUser(householdUser)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/rewards/ledger?size=201").with(asUser(householdUser)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/rewards/redemptions?size=201").with(asUser(householdUser)))
                .andExpect(status().isBadRequest());

        // Test admin endpoints
        mockMvc.perform(get("/api/admin/users?size=201").with(asUser(adminUser)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/pickups?size=201").with(asUser(adminUser)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/audit?size=201").with(asUser(adminUser)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endpointsRejectSizeLessThanOneWith400() throws Exception {
        mockMvc.perform(get("/api/notifications?size=0").with(asUser(householdUser)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/rewards/ledger?size=0").with(asUser(householdUser)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/rewards/redemptions?size=0").with(asUser(householdUser)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/users?size=0").with(asUser(adminUser)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/pickups?size=0").with(asUser(adminUser)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/audit?size=0").with(asUser(adminUser)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endpointsRejectNegativePageWith400() throws Exception {
        mockMvc.perform(get("/api/notifications?page=-1").with(asUser(householdUser)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/rewards/ledger?page=-1").with(asUser(householdUser)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/rewards/redemptions?page=-1").with(asUser(householdUser)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/users?page=-1").with(asUser(adminUser)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/pickups?page=-1").with(asUser(adminUser)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/audit?page=-1").with(asUser(adminUser)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endpointsReturnPageResponseWithDefaultPage0AndSize20() throws Exception {
        mockMvc.perform(get("/api/notifications").with(asUser(householdUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/rewards/ledger").with(asUser(householdUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/rewards/redemptions").with(asUser(householdUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/admin/users").with(asUser(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/admin/pickups").with(asUser(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/admin/audit").with(asUser(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void outOfRangePageReturns200WithEmptyContent() throws Exception {
        mockMvc.perform(get("/api/notifications?page=9999").with(asUser(householdUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.number").value(9999));

        mockMvc.perform(get("/api/rewards/ledger?page=9999").with(asUser(householdUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.number").value(9999));

        mockMvc.perform(get("/api/admin/users?page=9999").with(asUser(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.number").value(9999));
    }
}
