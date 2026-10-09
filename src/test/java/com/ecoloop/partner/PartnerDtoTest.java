package com.ecoloop.partner;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartnerDtoTest {

    @Test
    void testFromPartnerWithWarehouseId() {
        Partner p = new Partner(UUID.randomUUID(), "Org", "type", "lic");
        p.setWarehouseId("WH-001");

        PartnerDto dto = PartnerDto.from(p, true);

        assertEquals("WH-001", dto.warehouseId());
    }
}
