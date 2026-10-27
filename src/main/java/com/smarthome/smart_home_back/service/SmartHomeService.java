package com.smarthome.smart_home_back.service;

import com.smarthome.smart_home_back.mqtt.MqttTransport;
import com.smarthome.smart_home_back.mqtt.PahoMqttTransport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SmartHomeService {

    private static final JsonMapper JSON =
            JsonMapper.builder().build();

    private static final Set<String> LIGHTS =
            Set.of(
                    "luz_sala",
                    "luz_comedor",
                    "luz_cocina",
                    "luz_habitacion"
            );

    private final MqttTransport mqtt;
    private final Clock clock;

    private final AtomicReference<Pending> pending =
            new AtomicReference<>();

    private volatile Sample sample;

    private volatile boolean availability = false;

    private volatile Map<String, Object> lastEvent =
            Map.of();

    record Sample(
            JsonNode data,
            Instant at
    ) {
    }

    record Pending(
            String requestId,
            String id,
            CompletableFuture<Map<String, Object>> future
    ) {
    }

    public record Command(
            String id,
            String accion,
            Object valor
    ) {
    }

    public record Status(
            boolean mqttConnected,
            boolean espOnline,
            boolean fresh,
            boolean busy,
            String receivedAt,
            JsonNode estado,
            Map<String, Object> evento
    ) {
    }

    @Autowired
    public SmartHomeService(MqttTransport mqtt) {
        this(
                mqtt,
                Clock.systemUTC()
        );
    }

    SmartHomeService(
            MqttTransport mqtt,
            Clock clock
    ) {

        this.mqtt = mqtt;
        this.clock = clock;

        mqtt.setReceiver(
                this::receive
        );
    }

    public Status status() {

        Sample current = sample;

        boolean fresh =
                current != null
                        && clock.millis()
                        - current.at().toEpochMilli()
                        < 4000;

        boolean online =
                mqtt.connected()
                        && availability
                        && fresh;

        return new Status(
                mqtt.connected(),
                online,
                online,
                pending.get() != null,
                current == null
                        ? null
                        : current.at().toString(),
                online
                        ? current.data()
                        : null,
                lastEvent
        );
    }

    private static int integer(Object value) {

        if (!(value instanceof Integer)
                && !(value instanceof Long)) {

            return -1;
        }

        long n =
                ((Number) value).longValue();

        return n >= 0 && n <= 2999
                ? (int) n
                : -1;
    }

    static void validate(Command command) {

        if (command == null
                || command.id() == null
                || command.accion() == null) {

            bad("Faltan id y accion");
        }

        int n =
                integer(command.valor());

        boolean valid;

        if (LIGHTS.contains(command.id())
                || command.id().equals("alarma")) {

            valid =
                    command.accion().equals("estado")
                            && n >= 0
                            && n <= 1;

        } else {

            valid =
                    switch (command.id()) {

                        case "puerta" ->

                                (
                                        command.accion()
                                                .equals("estado")
                                                && n >= 0
                                                && n <= 1
                                )
                                        ||
                                        (
                                                command.accion()
                                                        .equals("angulo")
                                                        && n >= 0
                                                        && n <= 180
                                        );

                        case "ventilador" ->

                                (
                                        command.accion()
                                                .equals("velocidad")
                                                && n >= 0
                                                && n <= 100
                                )
                                        ||
                                        (
                                                command.accion()
                                                        .equals("estado")
                                                        && n >= 0
                                                        && n <= 1
                                        )
                                        ||
                                        (
                                                command.accion()
                                                        .equals("modo")
                                                        &&
                                                        (
                                                                "web".equals(
                                                                        command.valor()
                                                                )
                                                                        ||
                                                                        "manual".equals(
                                                                                command.valor()
                                                                        )
                                                        )
                                        );

                        case "audio" ->

                                (
                                        command.accion()
                                                .equals("pista")
                                                && n >= 1
                                                && n <= 2999
                                )
                                        ||
                                        (
                                                command.accion()
                                                        .equals("volumen")
                                                        && n >= 0
                                                        && n <= 30
                                        )
                                        ||
                                        (
                                                command.accion()
                                                        .equals("parar")
                                                        && n == 0
                                        );

                        default -> false;
                    };
        }

        if (!valid) {

            bad(
                    "Componente, acción o valor inválido"
            );
        }
    }

    private static void bad(String message) {

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    public CompletableFuture<Map<String, Object>>
    command(Command command) {

        validate(command);

        Status status =
                status();

        String node =
                (
                        command.id().equals("ventilador")
                                ||
                                command.id().equals("audio")
                )
                        ? "uno2"
                        : "uno1";

        if (!status.espOnline()
                ||
                !status.estado()
                        .path(node)
                        .path("online")
                        .asBoolean(false)) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ESP32 o Arduino sin datos recientes"
            );
        }

        if (
                command.id().equals("ventilador")
                        &&
                        !command.accion().equals("modo")
                        &&
                        !(
                                command.accion()
                                        .equals("estado")
                                        &&
                                        integer(command.valor()) == 0
                        )
                        &&
                        status.estado()
                                .path("uno2")
                                .path("ventilador")
                                .path("modo")
                                .asText()
                                .equals("manual")
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Selecciona modo web para controlar la velocidad"
            );
        }

        String requestId =
                UUID.randomUUID().toString();

        Pending pendingCommand =
                new Pending(
                        requestId,
                        command.id(),
                        new CompletableFuture<>()
                );

        if (!pending.compareAndSet(
                null,
                pendingCommand
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Hay una orden esperando confirmación"
            );
        }

        var response =
                pendingCommand.future()
                        .whenComplete(
                                (result, error) ->
                                        pending.compareAndSet(
                                                pendingCommand,
                                                null
                                        )
                        );

        // HTTP no se considera exitoso hasta recibir
        // el ACK MQTT correlacionado.

        pendingCommand.future()
                .completeOnTimeout(
                        result(
                                pendingCommand,
                                "incierto",
                                "No llegó confirmación. Consulta el estado antes de repetir."
                        ),
                        5,
                        TimeUnit.SECONDS
                );

        try {

            mqtt.publish(
                    JSON.writeValueAsString(
                            Map.of(
                                    "request_id",
                                    requestId,
                                    "id",
                                    command.id(),
                                    "accion",
                                    command.accion(),
                                    "valor",
                                    command.valor()
                            )
                    )
            );

        } catch (Exception e) {

            pendingCommand.future()
                    .complete(
                            result(
                                    pendingCommand,
                                    "incierto",
                                    "No se pudo confirmar el envío MQTT. Consulta el estado antes de repetir."
                            )
                    );
        }

        return response;
    }

    private static Map<String, Object> result(
            Pending pending,
            String result,
            String detail
    ) {

        return Map.of(
                "request_id",
                pending.requestId(),
                "id",
                pending.id(),
                "resultado",
                result,
                "detalle",
                detail
        );
    }

    void receive(
            String topic,
            String payload,
            boolean retained
    ) {

        if (topic.equals(
                PahoMqttTransport.ROOT
                        + "availability"
        )) {

            availability =
                    payload.trim()
                            .equals("online");

            if (!availability) {
                sample = null;
            }

            return;
        }

        // ACK y telemetría retained no cuentan
        // como evidencia actual.

        if (retained) {
            return;
        }

        try {

            JsonNode data =
                    JSON.readTree(payload);

            if (data == null
                    || !data.isObject()) {

                return;
            }

            if (
                    topic.equals(
                            PahoMqttTransport.ROOT
                                    + "status"
                    )
                            &&
                            data.path("tipo")
                                    .asText()
                                    .equals("estado")
                            &&
                            data.path("version")
                                    .asInt() == 1
                            &&
                            data.path("uno1")
                                    .isObject()
                            &&
                            data.path("uno2")
                                    .isObject()
                            &&
                            data.path("uno1")
                                    .path("online")
                                    .isBoolean()
                            &&
                            data.path("uno2")
                                    .path("online")
                                    .isBoolean()
            ) {

                sample =
                        new Sample(
                                data,
                                clock.instant()
                        );

                availability = true;

            } else if (
                    topic.equals(
                            PahoMqttTransport.ROOT
                                    + "ack"
                    )
                            &&
                            data.path("tipo")
                                    .asText()
                                    .equals("ack")
            ) {

                Pending pendingCommand =
                        pending.get();

                if (
                        pendingCommand == null
                                ||
                                !pendingCommand
                                        .requestId()
                                        .equals(
                                                data.path(
                                                                "request_id"
                                                        )
                                                        .asText()
                                        )
                                ||
                                !pendingCommand
                                        .id()
                                        .equals(
                                                data.path("id")
                                                        .asText()
                                        )
                ) {

                    return;
                }

                String result =
                        data.path("resultado")
                                .asText();

                if (
                        Set.of(
                                "aplicado",
                                "rechazado",
                                "incierto"
                        ).contains(result)
                ) {

                    pendingCommand.future()
                            .complete(
                                    result(
                                            pendingCommand,
                                            result,
                                            data.path(
                                                            "detalle"
                                                    )
                                                    .asText()
                                    )
                            );
                }

            } else if (
                    topic.equals(
                            PahoMqttTransport.ROOT
                                    + "event"
                    )
                            &&
                            data.path("tipo")
                                    .asText()
                                    .equals("tecla")
            ) {

                String key =
                        data.path("tecla")
                                .asText();

                if (
                        key.matches(
                                "[0-9A-D*#]"
                        )
                ) {

                    lastEvent =
                            Map.of(
                                    "tecla",
                                    key,

                                    "contador",
                                    data.path(
                                                    "contador"
                                            )
                                            .asInt(),

                                    "receivedAt",
                                    clock.instant()
                                            .toString()
                            );
                }
            }

        } catch (Exception ignored) {

            // Mensajes MQTT inválidos no deben
            // derribar el callback.
        }
    }
}