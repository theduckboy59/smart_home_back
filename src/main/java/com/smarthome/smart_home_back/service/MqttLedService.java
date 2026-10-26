package com.smarthome.smart_home_back.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MqttLedService {

    @Value("${mqtt.broker:tcp://127.0.0.1:1883}")
    private String broker;

    @Value("${mqtt.client-id:smart-home-backend}")
    private String clientId;

    @Value("${mqtt.command-topic:smart_home/esp32/command}")
    private String commandTopic;

    @Value("${mqtt.status-topic:smart_home/esp32/status}")
    private String statusTopic;

    private MqttClient client;

    private volatile String lastMessage = "MQTT no conectado";
    private volatile String lastMessageTime = "";

    private final Map<Integer, String> ledStates =
            new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        connect();
    }

    public synchronized boolean connect() {
        try {
            if (client != null && client.isConnected()) {
                return true;
            }

            if (client == null) {
                client = new MqttClient(
                        broker,
                        clientId,
                        new MemoryPersistence()
                );

                client.setCallback(new MqttCallback() {
                    @Override
                    public void connectionLost(Throwable cause) {
                        lastMessage = "Conexión MQTT perdida";
                        lastMessageTime = Instant.now().toString();
                    }

                    @Override
                    public void messageArrived(
                            String topic, MqttMessage message) {

                        String payload = new String(
                                message.getPayload(),
                                StandardCharsets.UTF_8
                        ).trim();

                        lastMessage = payload;
                        lastMessageTime = Instant.now().toString();

                        processStatus(payload);
                    }

                    @Override
                    public void deliveryComplete(
                            IMqttDeliveryToken token) {
                    }
                });
            }

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(5);
            options.setKeepAliveInterval(30);

            client.connect(options);
            client.subscribe(statusTopic, 1);

            lastMessage = "Conectado a Mosquitto";
            lastMessageTime = Instant.now().toString();

            System.out.println("Conectado a MQTT: " + broker);
            return true;

        } catch (MqttException e) {
            lastMessage = "MQTT no disponible: " + e.getMessage();
            lastMessageTime = Instant.now().toString();

            System.err.println(lastMessage);
            return false;
        }
    }

    public synchronized void publishCommand(String command)
            throws MqttException {

        if (client == null || !client.isConnected()) {
            if (!connect()) {
                throw new MqttException(
                        MqttException.REASON_CODE_CLIENT_NOT_CONNECTED
                );
            }
        }

        MqttMessage message = new MqttMessage(
                command.getBytes(StandardCharsets.UTF_8)
        );

        message.setQos(1);
        message.setRetained(false);

        client.publish(commandTopic, message);

        lastMessage = "Comando publicado: " + command;
        lastMessageTime = Instant.now().toString();

        System.out.println(
                "MQTT publicado en " + commandTopic + ": " + command
        );
    }

    public void controlLed(int led, boolean turnOn)
            throws MqttException {

        String command;

        if (led == 1) {
            command = turnOn ? "LED_ON" : "LED_OFF";
        } else if (led == 2) {
            command = turnOn ? "LED2_ON" : "LED2_OFF";
        } else {
            throw new IllegalArgumentException(
                    "LED no válido. Utiliza LED 1 o LED 2."
            );
        }

        publishCommand(command);
    }

    private void processStatus(String payload) {
        switch (payload) {
            case "LED_ON_OK" ->
                    ledStates.put(1, "ENCENDIDO");

            case "LED_OFF_OK" ->
                    ledStates.put(1, "APAGADO");

            case "LED2_ON_OK" ->
                    ledStates.put(2, "ENCENDIDO");

            case "LED2_OFF_OK" ->
                    ledStates.put(2, "APAGADO");

            default -> {
                // Se conserva el mensaje para diagnóstico.
            }
        }
    }

    public Map<String, Object> getStatus() {
        return Map.of(
                "mqttConnected",
                client != null && client.isConnected(),
                "led1",
                ledStates.getOrDefault(1, "DESCONOCIDO"),
                "led2",
                ledStates.getOrDefault(2, "DESCONOCIDO"),
                "lastMessage",
                lastMessage,
                "lastMessageTime",
                lastMessageTime
        );
    }

    @PreDestroy
    public synchronized void disconnect() {
        try {
            if (client != null) {
                if (client.isConnected()) {
                    client.disconnect();
                }

                client.close();
            }
        } catch (MqttException e) {
            System.err.println(
                    "Error cerrando MQTT: " + e.getMessage()
            );
        }
    }
}
