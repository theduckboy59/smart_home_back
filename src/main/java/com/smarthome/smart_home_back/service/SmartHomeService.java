package com.smarthome.smart_home_back.service;

import com.smarthome.smart_home_back.dto.SystemStatusDTO;
import com.smarthome.smart_home_back.dto.SystemStatusDTO.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class SmartHomeService {

    private final Instant startedAt = Instant.now();

    public SystemStatusDTO getStatus() {
        return new SystemStatusDTO(
                buildBackend(),
                buildFrontend(),
                Instant.now()
        );
    }

    private Backend buildBackend() {
        long uptime = Instant.now().getEpochSecond() - startedAt.getEpochSecond();
        return new Backend("ONLINE", 8080, uptime);
    }

    private Frontend buildFrontend() {
        return new Frontend(List.of(
                "http://localhost:4200",
                "http://100.80.25.81:4200",
                "http://10.98.33.20:4200"
        ));
    }
}