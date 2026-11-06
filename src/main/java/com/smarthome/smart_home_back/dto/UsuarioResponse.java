package com.smarthome.smart_home_back.dto;

public record UsuarioResponse(
        Long id,
        String nombre,
        boolean passwordappConfigurada,
        boolean passwordpuertaConfigurada
) {
}
