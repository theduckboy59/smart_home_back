package com.smarthome.smart_home_back.dto;

import java.time.Instant;
import java.util.List;

public record SystemStatusDTO(
        Backend backend,
        Frontend frontend,
        Instant timestamp
) {
    public record Backend(String status, int port, long uptimeSeconds) {}
    public record Frontend(List<String> allowedOrigins) {}
}