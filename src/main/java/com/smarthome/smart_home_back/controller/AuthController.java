package com.smarthome.smart_home_back.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import com.smarthome.smart_home_back.dto.LoginRequest;
import com.smarthome.smart_home_back.dto.LoginResponse;
import com.smarthome.smart_home_back.service.AuthService;
import com.smarthome.smart_home_back.service.SesionService;
import com.smarthome.smart_home_back.model.Usuario;
import com.smarthome.smart_home_back.config.AutorizacionConfig;

import java.util.Map;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SesionService sesionService;

    public AuthController(AuthService authService, SesionService sesionService) {
        this.authService = authService;
        this.sesionService = sesionService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        LoginResponse respuesta = authService.autenticar(
                request.nombre(),
                request.contrasena()
        );

        if (!respuesta.autenticado()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(respuesta);
        }

        return ResponseEntity.ok(respuesta);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        sesionService.revocar(sesionService.extraerToken(authorization));
        return ResponseEntity.ok(Map.of("cerrada", true));
    }

    @GetMapping("/me")
    public Map<String, Object> me(
            @RequestAttribute(AutorizacionConfig.USER_ATTRIBUTE) Usuario usuario) {
        return Map.of("id", usuario.getId(), "nombre", usuario.getNombre());
    }
}
