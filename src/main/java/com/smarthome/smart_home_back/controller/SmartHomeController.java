package com.smarthome.smart_home_back.controller;

import com.smarthome.smart_home_back.service.SmartHomeService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/smart-home")
public class SmartHomeController {

    private final SmartHomeService service;

    public SmartHomeController(SmartHomeService service) {
        this.service = service;
    }

    @GetMapping("status")
    public SmartHomeService.Status status() {
        return service.status();
    }

    @PostMapping("commands")
    public CompletableFuture<Map<String, Object>> command(
            @RequestBody SmartHomeService.Command command) {

        return service.command(command);
    }
}