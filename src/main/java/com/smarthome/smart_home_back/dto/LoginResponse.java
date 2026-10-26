package com.smarthome.smart_home_back.dto;

public record LoginResponse(
        boolean autenticado,
        String mensaje,
        Long usuarioId,
        String nombre
) {
}