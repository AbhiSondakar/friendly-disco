package com.ecoloop.pickup;

public record VerifyRequest(
    String category,
    String condition,
    String notes,
    String evidenceUrl) {}
