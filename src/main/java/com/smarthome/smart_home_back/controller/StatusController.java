package com.smarthome.smart_home_back.controller;

import com.smarthome.smart_home_back.dto.SystemStatusDTO;
import com.smarthome.smart_home_back.service.SmartHomeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class StatusController {

    private final SmartHomeService service;

    public StatusController(SmartHomeService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public SystemStatusDTO status() {
        return service.getStatus();
    }
}