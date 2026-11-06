package com.smarthome.smart_home_back.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class MqttEventProcessor {
    private final AccesoService acceso;
    private final SeguridadPerimetralService seguridad;
    private final ExecutorService queue = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "smart-home-mqtt-events");
        thread.setDaemon(true);
        return thread;
    });

    public MqttEventProcessor(AccesoService acceso, SeguridadPerimetralService seguridad) {
        this.acceso = acceso;
        this.seguridad = seguridad;
    }

    public void pin(String code) {
        queue.execute(() -> acceso.procesarPin(code));
    }

    public void distance(boolean valid, int centimeters) {
        queue.execute(() -> seguridad.recibirMedicion(valid, centimeters));
    }

    @PreDestroy
    public void close() {
        queue.shutdownNow();
    }
}
