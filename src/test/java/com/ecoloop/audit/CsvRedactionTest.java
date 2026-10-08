package com.ecoloop.audit;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CsvRedactionTest {

    @Test
    void auditLoggingInterceptorAndCsvExporterShareIdenticalRedactionKeys() throws Exception {
        Field interceptorField = AuditLoggingInterceptor.class.getDeclaredField("SENSITIVE_FIELD_NAMES");
        interceptorField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> interceptorKeys = (Set<String>) interceptorField.get(null);

        assertSame(AuditRedactionKeys.SENSITIVE_FIELD_NAMES, interceptorKeys,
                "AuditLoggingInterceptor must reference AuditRedactionKeys.SENSITIVE_FIELD_NAMES directly");

        assertTrue(AuditRedactionKeys.isSensitive("user_password"));
        assertTrue(AuditRedactionKeys.isSensitive("authToken"));
        assertTrue(AuditRedactionKeys.isSensitive("client_secret"));
        assertTrue(AuditRedactionKeys.isSensitive("credit_card_number"));
        assertFalse(AuditRedactionKeys.isSensitive("category"));
    }

    @Test
    void escapeCsvPreventsFormulaInjection() {
        // Formula injection prefixes: =, +, -, @, \t, \r
        assertEquals("'=cmd|' /C calc'!A0", AdminAuditController.escapeCsv("=cmd|' /C calc'!A0"));
        assertEquals("'@SUM(A1:A10)", AdminAuditController.escapeCsv("@SUM(A1:A10)"));
        assertEquals("'+12345", AdminAuditController.escapeCsv("+12345"));
        assertEquals("'-54321", AdminAuditController.escapeCsv("-54321"));
        assertEquals("Normal Text", AdminAuditController.escapeCsv("Normal Text"));
        assertEquals("\"Quotes, and commas\"", AdminAuditController.escapeCsv("Quotes, and commas"));
    }
}
