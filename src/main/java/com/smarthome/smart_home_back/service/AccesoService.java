package com.smarthome.smart_home_back.service;

import com.smarthome.smart_home_back.model.EventoSeguridad;
import com.smarthome.smart_home_back.model.Usuario;
import com.smarthome.smart_home_back.repository.EventoSeguridadRepository;
import com.smarthome.smart_home_back.repository.UsuarioRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionException;

@Service
public class AccesoService {
    private static final String PIN_PATTERN = "^[1-9A-D]{4}$";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final EventoSeguridadRepository eventos;
    private final ObjectProvider<SmartHomeService> smartHomeProvider;
    private final ObjectProvider<SeguridadPerimetralService> seguridadProvider;

    private int failedAttempts;
    private Instant blockedUntil;

    public AccesoService(UsuarioRepository usuarios,
                         PasswordEncoder encoder,
                         EventoSeguridadRepository eventos,
                         ObjectProvider<SmartHomeService> smartHomeProvider,
                         ObjectProvider<SeguridadPerimetralService> seguridadProvider) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.eventos = eventos;
        this.smartHomeProvider = smartHomeProvider;
        this.seguridadProvider = seguridadProvider;
    }

    public synchronized void procesarPin(String pin) {
        Instant now = Instant.now();
        if (blockedUntil != null && blockedUntil.isAfter(now)) {
            log("ACCESO_RECHAZADO", "BLOQUEADO", null, null,
                    "Validación temporalmente bloqueada");
            return;
        }
        if (pin == null || !pin.matches(PIN_PATTERN)) {
            failed("PIN rechazado por formato");
            return;
        }

        List<Usuario> matches = usuarios.findAll().stream()
                .filter(user -> !Boolean.FALSE.equals(user.getActivo()))
                .filter(user -> user.getPasswordpuerta() != null)
                .filter(user -> encoder.matches(pin, user.getPasswordpuerta()))
                .toList();
        if (matches.size() != 1) {
            failed(matches.isEmpty() ? "PIN no autorizado" : "PIN ambiguo; acceso denegado");
            return;
        }

        Usuario user = matches.get(0);
        failedAttempts = 0;
        blockedUntil = null;
        if (seguridadProvider.getObject().alarmaActiva()) {
            log("ACCESO_RECHAZADO", "RECHAZADO", user.getId(), null,
                    "La alarma está activa; la puerta permanece bloqueada");
            return;
        }

        SmartHomeService smartHome = smartHomeProvider.getObject();
        SmartHomeService.Status status = smartHome.status();
        if (!status.espOnline() || status.estado() == null
                || !status.estado().path("uno1").path("online").asBoolean(false)) {
            log("ACCESO_RECHAZADO", "INCIERTO", user.getId(), null,
                    "Dispositivos sin conexión reciente");
            return;
        }

        try {
            smartHome.commandAuthorized(
                    new SmartHomeService.Command("puerta", "estado", 1),
                    SmartHomeService.CommandAuthorization.ACCESS_PIN
            ).whenComplete((result, error) -> {
                if (error != null) {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null
                            ? error.getCause() : error;
                    log("ACCESO_AUTORIZADO", "INCIERTO", user.getId(), null,
                            "No se pudo confirmar la orden de apertura: " + safeMessage(cause));
                    return;
                }
                String outcome = String.valueOf(result.getOrDefault("resultado", "incierto"));
                if ("aplicado".equals(outcome)) {
                    synchronized (this) { failedAttempts = 0; blockedUntil = null; }
                }
                log("ACCESO_AUTORIZADO", outcome.toUpperCase(), user.getId(),
                        String.valueOf(result.get("request_id")),
                        "Orden de apertura " + outcome);
            });
        } catch (RuntimeException e) {
            log("ACCESO_RECHAZADO", "INCIERTO", user.getId(), null,
                    "No se pudo enviar la orden de apertura: " + safeMessage(e));
        }
    }

    private synchronized void failed(String detail) {
        failedAttempts++;
        if (failedAttempts >= 5) {
            blockedUntil = Instant.now().plusSeconds(60);
            failedAttempts = 0;
            detail += "; bloqueo de 60 segundos aplicado";
        }
        log("ACCESO_RECHAZADO", "RECHAZADO", null, null, detail);
    }

    private void log(String type, String result, Long userId, String requestId, String detail) {
        EventoSeguridad event = new EventoSeguridad();
        event.setFecha(Instant.now());
        event.setTipo(type);
        event.setResultado(result);
        event.setUsuarioId(userId);
        event.setRequestId(requestId);
        event.setDetalle(detail.length() > 255 ? detail.substring(0, 255) : detail);
        eventos.save(event);
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }
}
