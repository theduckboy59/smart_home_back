package com.smarthome.smart_home_back.mqtt;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@EnableScheduling
public class PahoMqttTransport implements MqttTransport {

    private static final Logger log =
            LoggerFactory.getLogger(PahoMqttTransport.class);

    private final String broker;
    private final String clientId;
    private final String username;
    private final String password;
    private final boolean enabled;

    private volatile boolean ready;

    private volatile Receiver receiver = (t, p, r) -> {};

    private MqttClient client;

    public static final String ROOT = "smart_home/esp32/";

    public PahoMqttTransport(
            @Value("${app.mqtt.broker-url:tcp://127.0.0.1:1883}")
            String broker,

            @Value("${app.mqtt.client-id:smart-home-backend}")
            String clientId,

            @Value("${app.mqtt.username:}")
            String username,

            @Value("${app.mqtt.password:}")
            String password,

            @Value("${app.mqtt.enabled:true}")
            boolean enabled) {

        this.broker = broker;
        this.clientId = clientId;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
    }

    @PostConstruct
    void initialize() throws MqttException {

        if (!enabled) {
            return;
        }

        client = new MqttClient(
                broker,
                clientId,
                new MemoryPersistence()
        );

        client.setTimeToWait(2500);

        client.setCallback(new MqttCallback() {

            @Override
            public void connectionLost(Throwable cause) {
                ready = false;
                log.warn("Conexión MQTT perdida");
            }

            @Override
            public void deliveryComplete(
                    IMqttDeliveryToken token) {
            }

            @Override
            public void messageArrived(
                    String topic,
                    MqttMessage message) {

                if (message.getPayload().length > 8192) {
                    return;
                }

                receiver.receive(
                        topic,
                        new String(
                                message.getPayload(),
                                StandardCharsets.UTF_8
                        ),
                        message.isRetained()
                );
            }
        });
    }

    // También reintenta cuando el broker estaba apagado
    // al arrancar Spring.
    //
    // Se vuelve a suscribir tras CADA reconexión
    // con cleanSession.

    @Scheduled(
            fixedDelay = 3000,
            initialDelay = 1000
    )
    public synchronized void reconnect() {

        if (!enabled || client == null || connected()) {
            return;
        }

        try {

            if (!client.isConnected()) {

                MqttConnectOptions options =
                        new MqttConnectOptions();

                options.setAutomaticReconnect(false);
                options.setCleanSession(true);
                options.setConnectionTimeout(2);
                options.setKeepAliveInterval(15);

                if (!username.isBlank()) {
                    options.setUserName(username);
                    options.setPassword(
                            password.toCharArray()
                    );
                }

                client.connect(options);
            }

            client.subscribe(
                    new String[]{
                            ROOT + "status",
                            ROOT + "ack",
                            ROOT + "availability",
                            ROOT + "event"
                    },
                    new int[]{
                            0,
                            0,
                            1,
                            0
                    }
            );

            ready = true;

            log.info(
                    "MQTT conectado y suscrito a estados, confirmaciones y eventos"
            );

        } catch (MqttException e) {

            ready = false;

            log.warn(
                    "MQTT no disponible; se reintentará (código {})",
                    e.getReasonCode()
            );
        }
    }

    @Override
    public boolean connected() {
        return ready
                && client != null
                && client.isConnected();
    }

    @Override
    public void setReceiver(Receiver receiver) {
        this.receiver = receiver;
    }

    @Override
    public void publish(String payload)
            throws MqttException {

        if (!connected()) {
            throw new MqttException(
                    MqttException.REASON_CODE_CLIENT_NOT_CONNECTED
            );
        }

        MqttMessage message =
                new MqttMessage(
                        payload.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        message.setQos(0);

        // Las acciones físicas nunca se publican retained.
        message.setRetained(false);

        client.publish(
                ROOT + "command",
                message
        );
    }

    @PreDestroy
    public synchronized void close() {

        ready = false;

        if (client == null) {
            return;
        }

        try {

            if (client.isConnected()) {
                client.disconnect();
            }

            client.close();

        } catch (MqttException e) {

            log.debug(
                    "Cierre MQTT: {}",
                    e.getReasonCode()
            );
        }
    }
}
