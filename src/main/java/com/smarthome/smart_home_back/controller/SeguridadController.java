package com.smarthome.smart_home_back.controller;

import com.smarthome.smart_home_back.config.AutorizacionConfig;
import com.smarthome.smart_home_back.model.EventoSeguridad;
import com.smarthome.smart_home_back.model.Usuario;
import com.smarthome.smart_home_back.repository.EventoSeguridadRepository;
import com.smarthome.smart_home_back.service.SeguridadPerimetralService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/seguridad")
public class SeguridadController {
    public record DesactivarRequest(@NotBlank String passwordapp) {}

    private final SeguridadPerimetralService seguridad;
    private final EventoSeguridadRepository eventos;

    public SeguridadController(SeguridadPerimetralService seguridad,
                               EventoSeguridadRepository eventos) {
        this.seguridad = seguridad;
        this.eventos = eventos;
    }

    @GetMapping("/estado")
    public SeguridadPerimetralService.EstadoRespuesta estado() {
        return seguridad.estado();
    }

    @GetMapping("/eventos")
    public Page<EventoSeguridad> eventos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        return eventos.findAll(PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "fecha")));
    }

    @PostMapping("/alarma/desactivar")
    public CompletableFuture<java.util.Map<String, Object>> desactivar(
            @Valid @RequestBody DesactivarRequest request,
            @RequestAttribute(AutorizacionConfig.USER_ATTRIBUTE) Usuario usuario) {
        return seguridad.desactivarAlarma(usuario, request.passwordapp());
    }
}
