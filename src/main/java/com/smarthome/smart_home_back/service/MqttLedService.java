package com.smarthome.smart_home_back.service;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/** Keeps the legacy endpoint shape without opening a second MQTT client. */
@Service
public class MqttLedService {
    private final SmartHomeService smartHome;

    public MqttLedService(SmartHomeService smartHome) {
        this.smartHome = smartHome;
    }

    public void controlLed(int led, boolean turnOn) throws MqttException {
        if (led != 1 && led != 2) {
            throw new IllegalArgumentException("LED no válido. Utiliza LED 1 o LED 2.");
        }
        // There is no documented mapping from legacy LED numbers to firmware IDs.
        throw new MqttException(MqttException.REASON_CODE_INVALID_MESSAGE);
    }

    public Map<String, Object> getStatus() {
        SmartHomeService.Status status = smartHome.status();
        return Map.of(
                "mqttConnected", status.mqttConnected(),
                "led1", "NO_DISPONIBLE",
                "led2", "NO_DISPONIBLE",
                "lastMessage", "El firmware JSON actual no reporta el estado de los LED heredados",
                "lastMessageTime", status.receivedAt() == null ? Instant.now().toString() : status.receivedAt()
        );
    }
}
