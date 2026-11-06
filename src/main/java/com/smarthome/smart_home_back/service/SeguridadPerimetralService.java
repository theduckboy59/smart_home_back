package com.smarthome.smart_home_back.service;

import com.smarthome.smart_home_back.model.EstadoSeguridad;
import com.smarthome.smart_home_back.model.EventoSeguridad;
import com.smarthome.smart_home_back.model.Usuario;
import com.smarthome.smart_home_back.repository.EstadoSeguridadRepository;
import com.smarthome.smart_home_back.repository.EventoSeguridadRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
public class SeguridadPerimetralService {
    public record EstadoRespuesta(
            String estado,
            Integer distanciaCm,
            boolean presencia,
            long tiempoPresenciaSegundos,
            int umbralCm,
            int advertenciaSegundo,
            int alarmaSegundo,
            boolean puertaBloqueada,
            boolean buzzerActivo,
            boolean esp32Online,
            boolean arduinoOnline,
            String ultimaMedicionValida
    ) {}

    private static final long SENSOR_TIMEOUT_MS = 2_000;
    private static final long PERSIST_INTERVAL_MS = 1_000;
    private static final String PIN_PATTERN = "^[1-9A-D]{4}$";

    private final EstadoSeguridadRepository estados;
    private final EventoSeguridadRepository eventos;
    private final PasswordEncoder encoder;
    private final ObjectProvider<SmartHomeService> smartHomeProvider;
    private EstadoSeguridad current;
    private Instant segmentStartedAt;
    private Instant lastPersistedAt = Instant.EPOCH;
    private Instant lastInvalidLogAt = Instant.EPOCH;
    private Instant lastAlarmCommandAt;
    private Instant lastAuthorizedRearmAt;
    private boolean alarmMismatchLogged;

    public SeguridadPerimetralService(EstadoSeguridadRepository estados,
                                      EventoSeguridadRepository eventos,
                                      PasswordEncoder encoder,
                                      ObjectProvider<SmartHomeService> smartHomeProvider) {
        this.estados = estados;
        this.eventos = eventos;
        this.encoder = encoder;
        this.smartHomeProvider = smartHomeProvider;
    }

    @PostConstruct
    @Transactional
    public synchronized void restore() {
        current = estados.findById(1L).orElseGet(() -> {
            EstadoSeguridad initial = new EstadoSeguridad();
            initial.setId(1L);
            initial.setEstado("NORMAL");
            initial.setEstadoDesde(Instant.now());
            return estados.save(initial);
        });
        // Después de reiniciar, no se cuenta el tiempo sin telemetría reciente.
        segmentStartedAt = null;
        if (current.isAlarmaActiva()) {
            current.setEstado("ALARMA");
            current.setPuertaBloqueada(true);
        } else if (current.isPresencia()) {
            current.setEstado("SENSOR_SIN_DATOS");
            current.setEstadoDesde(Instant.now());
            log("ERROR_SENSOR", "SIN_DATOS", null, null,
                    "Backend reiniciado durante vigilancia; se espera telemetría reciente");
        }
        saveState();
    }

    public synchronized boolean alarmaActiva() {
        ensureLoaded();
        reconcile(smartHomeProvider.getObject().status());
        return current.isAlarmaActiva();
    }

    @Transactional
    public synchronized void recibirMedicion(boolean valid, int centimeters) {
        ensureLoaded();
        Instant now = Instant.now();
        if (!valid || centimeters < 0 || centimeters > 500) {
            if (Duration.between(lastInvalidLogAt, now).toMillis() >= SENSOR_TIMEOUT_MS) {
                log("ERROR_SENSOR", "INVALIDA", null, null,
                        "Medición ultrasónica inválida; se ignora");
                lastInvalidLogAt = now;
            }
            return;
        }

        current.setDistanciaCm(centimeters);
        current.setUltimaMedicionValida(now);
        boolean withinPerimeter = centimeters <= 15;

        if (current.isAlarmaActiva()) {
            current.setPresencia(withinPerimeter);
            saveState();
            return;
        }

        if (!withinPerimeter) {
            if (current.isPresencia()) {
                current.setPresencia(false);
                current.setTiempoPresenciaMs(0);
                current.setPresenciaDesde(null);
                current.setAdvertenciaEnviada(false);
                segmentStartedAt = null;
                transition("NORMAL", "VIGILANCIA", "REINICIADA",
                        "La presencia salió del perímetro");
            }
            saveState();
            return;
        }

        if (!current.isPresencia()) {
            current.setPresencia(true);
            current.setTiempoPresenciaMs(0);
            current.setPresenciaDesde(now);
            current.setAdvertenciaEnviada(false);
            segmentStartedAt = now;
            transition("VIGILANCIA", "VIGILANCIA", "INICIADA",
                    "Presencia detectada dentro de 15 cm");
            saveState();
            return;
        }

        if (segmentStartedAt != null) {
            long gap = Math.max(0, Duration.between(segmentStartedAt, now).toMillis());
            current.setTiempoPresenciaMs(current.getTiempoPresenciaMs()
                    + Math.min(gap, SENSOR_TIMEOUT_MS));
            if (gap > SENSOR_TIMEOUT_MS && !current.getEstado().equals("SENSOR_SIN_DATOS")) {
                transition("SENSOR_SIN_DATOS", "ERROR_SENSOR", "SIN_DATOS",
                        "No se recibieron mediciones válidas durante más de 2 segundos");
            }
        }
        segmentStartedAt = now;
        saveState();
    }

    @Scheduled(fixedDelay = 200)
    @Transactional
    public synchronized void advance() {
        if (current == null) return;
        Instant now = Instant.now();
        if (current.isAlarmaActiva() || !current.isPresencia()) return;

        Instant last = current.getUltimaMedicionValida();
        if (last == null || Duration.between(last, now).toMillis() > SENSOR_TIMEOUT_MS) {
            if (segmentStartedAt != null) {
                long elapsed = Math.max(0, Duration.between(segmentStartedAt, now).toMillis());
                current.setTiempoPresenciaMs(current.getTiempoPresenciaMs()
                        + Math.min(elapsed, SENSOR_TIMEOUT_MS));
                segmentStartedAt = null;
            }
            if (!current.getEstado().equals("SENSOR_SIN_DATOS")) {
                transition("SENSOR_SIN_DATOS", "ERROR_SENSOR", "SIN_DATOS",
                        "No se reciben mediciones válidas desde hace más de 2 segundos");
            }
            saveState();
            return;
        }

        long elapsed = current.getTiempoPresenciaMs();
        if (segmentStartedAt != null) {
            elapsed += Math.max(0, Duration.between(segmentStartedAt, now).toMillis());
            current.setTiempoPresenciaMs(elapsed);
            segmentStartedAt = now;
        }

        if (current.getEstado().equals("SENSOR_SIN_DATOS")) {
            if (elapsed >= 15_000) {
                transition("PRESENCIA_CONFIRMADA", "VIGILANCIA", "CONFIRMADA",
                        "Mediciones recuperadas; la presencia supera 15 segundos");
            } else {
                transition("VIGILANCIA", "ERROR_SENSOR", "RECUPERADO",
                        "Se recuperaron las mediciones válidas");
            }
        }

        if (elapsed >= 15_000 && current.getEstado().equals("VIGILANCIA")) {
            transition("PRESENCIA_CONFIRMADA", "VIGILANCIA", "CONFIRMADA",
                    "La presencia continúa durante 15 segundos");
        }

        if (elapsed >= 25_000 && current.isAdvertenciaEnviada()
                && current.getEstado().equals("PRESENCIA_CONFIRMADA")) {
            transition("ADVERTENCIA", "ADVERTENCIA", "RECUPERADA",
                    "Se conserva el estado de advertencia sin repetir el audio");
        }

        if (elapsed >= 25_000 && !current.isAdvertenciaEnviada()) {
            current.setAdvertenciaEnviada(true);
            transition("ADVERTENCIA", "ADVERTENCIA", "PENDIENTE",
                    "Se solicita reproducir la pista 3");
            sendWarning();
        }

        if (elapsed >= 30_000 && !current.isAlarmaActiva()) {
            current.setAlarmaActiva(true);
            current.setPuertaBloqueada(true);
            transition("ALARMA", "ALARMA_ACTIVADA", "PENDIENTE",
                    "Se solicita activar buzzer y bloqueo de puerta");
            sendAlarmOn();
        }

        if (Duration.between(lastPersistedAt, now).toMillis() >= PERSIST_INTERVAL_MS) {
            saveState();
            lastPersistedAt = now;
        }
    }

    public synchronized EstadoRespuesta estado() {
        ensureLoaded();
        SmartHomeService.Status smartStatus = smartHomeProvider.getObject().status();
        reconcile(smartStatus);
        JsonNode data = smartStatus.estado();
        boolean arduinoOnline = data != null && data.path("uno1").path("online").asBoolean(false);
        long elapsed = current.getTiempoPresenciaMs();
        if (!current.isAlarmaActiva() && segmentStartedAt != null
                && current.getUltimaMedicionValida() != null
                && Duration.between(current.getUltimaMedicionValida(), Instant.now()).toMillis() <= SENSOR_TIMEOUT_MS) {
            elapsed += Math.max(0, Duration.between(segmentStartedAt, Instant.now()).toMillis());
        }
        return new EstadoRespuesta(current.getEstado(), current.getDistanciaCm(),
                current.isPresencia(), elapsed / 1000,
                15, 25, 30, current.isPuertaBloqueada(), current.isAlarmaActiva(),
                smartStatus.espOnline(), arduinoOnline,
                current.getUltimaMedicionValida() == null ? null : current.getUltimaMedicionValida().toString());
    }

    @Transactional
    public synchronized CompletableFuture<Map<String, Object>> desactivarAlarma(Usuario usuario, String passwordapp) {
        ensureLoaded();
        if (passwordapp == null || !passwordapp.matches(PIN_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña debe tener cuatro caracteres A-D o 1-9");
        }
        if (usuario.getPasswordapp() == null || !encoder.matches(passwordapp, usuario.getPasswordapp())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Contraseña incorrecta");
        }
        if (!current.isAlarmaActiva()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La alarma no está activa");
        }

        SmartHomeService smartHome = smartHomeProvider.getObject();
        CompletableFuture<Map<String, Object>> command;
        try {
            lastAlarmCommandAt = Instant.now();
            lastAuthorizedRearmAt = lastAlarmCommandAt;
            command = smartHome.commandAuthorized(
                    new SmartHomeService.Command("alarma", "estado", 0),
                    SmartHomeService.CommandAuthorization.ALARM_REARM);
        } catch (RuntimeException e) {
            log("ALARMA_DESACTIVADA", "INCIERTO", usuario.getId(), null,
                    "No se pudo enviar el rearme; se conserva la alarma activa");
            throw e;
        }

        return command.handle((result, error) -> {
            synchronized (this) {
                if (error != null) {
                    log("ALARMA_DESACTIVADA", "INCIERTO", usuario.getId(), null,
                            "No se recibió confirmación; se conserva la alarma activa");
                    return alarmResponse(true, "incierto", "activa",
                            "No se confirmó el rearme");
                }
                String outcome = String.valueOf(result.getOrDefault("resultado", "incierto"));
                String requestId = String.valueOf(result.getOrDefault("request_id", ""));
                if (outcome.equals("aplicado")) {
                    current.setAlarmaActiva(false);
                    current.setPuertaBloqueada(false);
                    if (current.isPresencia()
                            && current.getDistanciaCm() != null
                            && current.getDistanciaCm() <= 15
                            && current.getUltimaMedicionValida() != null
                            && Duration.between(current.getUltimaMedicionValida(), Instant.now()).toMillis() <= SENSOR_TIMEOUT_MS) {
                        current.setTiempoPresenciaMs(0);
                        current.setPresenciaDesde(Instant.now());
                        current.setAdvertenciaEnviada(false);
                        segmentStartedAt = Instant.now();
                        setState("VIGILANCIA");
                    } else {
                        current.setPresencia(false);
                        current.setTiempoPresenciaMs(0);
                        current.setPresenciaDesde(null);
                        current.setAdvertenciaEnviada(false);
                        segmentStartedAt = null;
                        setState("NORMAL");
                    }
                    log("ALARMA_DESACTIVADA", "APLICADO", usuario.getId(), requestId,
                            "Alarma desactivada mediante sesión autenticada");
                    saveState();
                    return alarmResponse(true, "aplicado", "desactivada",
                            "Alarma desactivada correctamente");
                }
                log("ALARMA_DESACTIVADA", outcome.toUpperCase(), usuario.getId(), requestId,
                        "El ESP32 no confirmó el rearme; se conserva la alarma activa");
                return alarmResponse(true, outcome, "activa",
                        "No se confirmó el rearme; la alarma permanece activa");
            }
        });
    }

    private void sendWarning() {
        try {
            smartHomeProvider.getObject().command(
                    new SmartHomeService.Command("audio", "pista", 3))
                    .whenComplete((result, error) -> {
                        String outcome = error == null
                                ? String.valueOf(result.getOrDefault("resultado", "incierto"))
                                : "incierto";
                        log("ADVERTENCIA", outcome.toUpperCase(), null,
                                error == null ? String.valueOf(result.get("request_id")) : null,
                                error == null ? "Resultado de la pista 3: " + outcome
                                        : "No se confirmó la pista 3");
                    });
        } catch (RuntimeException e) {
            log("ADVERTENCIA", "INCIERTO", null, null, "No se pudo solicitar la pista 3");
        }
    }

    private void sendAlarmOn() {
        try {
            lastAlarmCommandAt = Instant.now();
            smartHomeProvider.getObject().commandAuthorized(
                    new SmartHomeService.Command("alarma", "estado", 1),
                    SmartHomeService.CommandAuthorization.PERIMETER_ALARM)
                    .whenComplete((result, error) -> {
                        String outcome = error == null
                                ? String.valueOf(result.getOrDefault("resultado", "incierto"))
                                : "incierto";
                        log("ALARMA_ACTIVADA", outcome.toUpperCase(), null,
                                error == null ? String.valueOf(result.get("request_id")) : null,
                                error == null ? "Resultado de la activación: " + outcome
                                        : "No se confirmó la activación; se conserva el estado de alarma");
                    });
        } catch (RuntimeException e) {
            log("ALARMA_ACTIVADA", "INCIERTO", null, null,
                    "No se pudo enviar la activación; se conserva el estado de alarma");
        }
    }

    private void transition(String state, String eventType, String result, String detail) {
        setState(state);
        log(eventType, result, null, null, detail);
        saveState();
    }

    private void setState(String state) {
        if (!state.equals(current.getEstado())) {
            current.setEstado(state);
            current.setEstadoDesde(Instant.now());
        }
    }

    private synchronized void ensureLoaded() {
        if (current == null) restore();
    }

    private void saveState() {
        if (current != null) estados.save(current);
    }

    private void reconcile(SmartHomeService.Status smartStatus) {
        JsonNode data = smartStatus.estado();
        if (!smartStatus.espOnline() || data == null
                || !data.path("uno1").path("online").asBoolean(false)) return;
        Instant receivedAt = smartStatus.receivedAt() == null
                ? null : Instant.parse(smartStatus.receivedAt());
        if (lastAlarmCommandAt != null && (receivedAt == null || !receivedAt.isAfter(lastAlarmCommandAt))) return;

        boolean reportedActive = data.path("uno1").path("alarma").path("estado").asInt(0) == 1;
        if (current.isAlarmaActiva() && !reportedActive
                && (lastAuthorizedRearmAt == null || receivedAt == null
                || !receivedAt.isAfter(lastAuthorizedRearmAt))) {
            if (!alarmMismatchLogged) {
                log("ALARMA_RECONCILIADA", "INCIERTO", null, null,
                        "Telemetría reporta alarma apagada; se conserva el estado activo hasta un rearme autorizado");
                alarmMismatchLogged = true;
            }
            return;
        }
        if (reportedActive != current.isAlarmaActiva()) {
            current.setAlarmaActiva(reportedActive);
            current.setPuertaBloqueada(reportedActive);
            if (reportedActive) {
                setState("ALARMA");
            } else {
                segmentStartedAt = null;
                current.setTiempoPresenciaMs(0);
                current.setPresencia(false);
                current.setPresenciaDesde(null);
                current.setAdvertenciaEnviada(false);
                setState("NORMAL");
            }
            log("ALARMA_RECONCILIADA", reportedActive ? "ACTIVA" : "APAGADA",
                    null, null, "Estado reconciliado con telemetría reciente del dispositivo");
            saveState();
        }
        alarmMismatchLogged = false;
    }

    private synchronized void log(String type, String result, Long userId, String requestId, String detail) {
        EventoSeguridad event = new EventoSeguridad();
        event.setFecha(Instant.now());
        event.setTipo(type);
        event.setResultado(result);
        event.setUsuarioId(userId);
        event.setRequestId(requestId);
        if (current != null) event.setDistanciaCm(current.getDistanciaCm());
        event.setDetalle(detail.length() > 255 ? detail.substring(0, 255) : detail);
        eventos.save(event);
    }

    private static Map<String, Object> alarmResponse(boolean authorized,
                                                      String result,
                                                      String alarm,
                                                      String message) {
        return Map.of("autorizado", authorized, "resultado", result,
                "alarma", alarm, "mensaje", message);
    }
}
