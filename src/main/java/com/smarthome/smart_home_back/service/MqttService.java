
package com.smarthome.smart_home_back.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
public class MqttService {

    private final MessageHandler mqttHandler;

    public MqttService(
            @Value("${app.mqtt.broker-url:tcp://127.0.0.1:1883}")
            String brokerUrl,
            @Value("${app.mqtt.client-id:smart-home-backend}")
            String clientId,
            @Value("${app.mqtt.command-topic:smart_home/esp32/command}")
            String commandTopic) {

        MqttPahoMessageHandler handler =
                new MqttPahoMessageHandler(brokerUrl, clientId);

        handler.setDefaultTopic(commandTopic);
        handler.setDefaultQos(1);
        handler.setAsync(false);

        this.mqttHandler = handler;
    }

    public void publicarComando(String comando) {
        mqttHandler.handleMessage(
                MessageBuilder.withPayload(comando).build()
        );
    }

    public void encenderLed() {
        publicarComando("LED_ON");
    }

    public void apagarLed() {
        publicarComando("LED_OFF");
    }
}
