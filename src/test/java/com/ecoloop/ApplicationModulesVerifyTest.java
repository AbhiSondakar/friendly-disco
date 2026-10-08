package com.ecoloop;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationModulesVerifyTest {

    @Test
    @Disabled("Phase 4")
    void verifyModulithStructure() {
        ApplicationModules.of(EcoLoopApplication.class).verify();
    }
}
