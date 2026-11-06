package com.smarthome.smart_home_back.controller;

import com.smarthome.smart_home_back.config.AutorizacionConfig;
import com.smarthome.smart_home_back.dto.ActualizarUsuarioRequest;
import com.smarthome.smart_home_back.dto.CrearUsuarioRequest;
import com.smarthome.smart_home_back.dto.UsuarioResponse;
import com.smarthome.smart_home_back.model.Usuario;
import com.smarthome.smart_home_back.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {
    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(
            @Valid @RequestBody CrearUsuarioRequest request,
            @RequestAttribute(AutorizacionConfig.USER_ATTRIBUTE) Usuario actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request, actor));
    }

    @GetMapping
    public List<UsuarioResponse> listar(
            @RequestAttribute(AutorizacionConfig.USER_ATTRIBUTE) Usuario actor) {
        return service.listar(actor);
    }

    @GetMapping("/me")
    public UsuarioResponse me(
            @RequestAttribute(AutorizacionConfig.USER_ATTRIBUTE) Usuario actor) {
        return service.miCuenta(actor);
    }

    @GetMapping("/{id}")
    public UsuarioResponse buscar(@PathVariable Long id,
            @RequestAttribute(AutorizacionConfig.USER_ATTRIBUTE) Usuario actor) {
        return service.buscar(id, actor);
    }

    @PutMapping("/{id}")
    public UsuarioResponse actualizar(@PathVariable Long id,
            @Valid @RequestBody ActualizarUsuarioRequest request,
            @RequestAttribute(AutorizacionConfig.USER_ATTRIBUTE) Usuario actor) {
        return service.actualizar(id, request, actor);
    }

    @PutMapping("/me")
    public UsuarioResponse actualizarMe(
            @Valid @RequestBody ActualizarUsuarioRequest request,
            @RequestAttribute(AutorizacionConfig.USER_ATTRIBUTE) Usuario actor) {
        return service.actualizar(actor.getId(), request, actor);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id,
            @RequestAttribute(AutorizacionConfig.USER_ATTRIBUTE) Usuario actor) {
        service.eliminar(id, actor);
        return ResponseEntity.noContent().build();
    }
}
