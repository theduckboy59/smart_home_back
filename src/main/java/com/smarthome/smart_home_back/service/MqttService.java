package com.smarthome.smart_home_back.service;

import org.springframework.stereotype.Service;

/**
 * Compatibility facade for the former text-command MQTT implementation.
 * The current firmware only accepts JSON commands through SmartHomeService.
 */
@Service
public class MqttService {
    public void publicarComando(String comando) {
        throw new UnsupportedOperationException(
                "El firmware actual no admite comandos MQTT de texto");
    }

    public void encenderLed() {
        publicarComando("LED_ON");
    }

    public void apagarLed() {
        publicarComando("LED_OFF");
    }
}
