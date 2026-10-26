
package com.smarthome.smart_home_back.controller;

import com.smarthome.smart_home_back.service.MqttLedService;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/leds")
@CrossOrigin(origins = {
        "http://localhost:4200",
        "http://127.0.0.1:4200",
        "https://smarthome.aeropuertolosprimos.me",
        "https://prueba.aeropuertolosprimos.me"
})
public class LedController {

    private final MqttLedService mqttLedService;

    public LedController(MqttLedService mqttLedService) {
        this.mqttLedService = mqttLedService;
    }

    @PostMapping("/{led}/on")
    public ResponseEntity<Map<String, Object>> turnOn(
            @PathVariable int led) {
        return executeCommand(led, true);
    }

    @PostMapping("/{led}/off")
    public ResponseEntity<Map<String, Object>> turnOff(
            @PathVariable int led) {
        return executeCommand(led, false);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(mqttLedService.getStatus());
    }

    private ResponseEntity<Map<String, Object>> executeCommand(
            int led, boolean turnOn) {

        try {
            mqttLedService.controlLed(led, turnOn);

            return ResponseEntity.accepted().body(Map.of(
                    "success", true,
                    "message", "Comando MQTT publicado",
                    "led", led,
                    "action", turnOn ? "ON" : "OFF"
            ));

        } catch (MqttException e) {
            return ResponseEntity.status(
                    HttpStatus.SERVICE_UNAVAILABLE
            ).body(Map.of(
                    "success", false,
                    "message", "No se pudo enviar el comando MQTT"
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
