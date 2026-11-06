package com.smarthome.smart_home_back.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ActualizarUsuarioRequest(
        @Size(max = 100) String nombre,
        @Size(max = 72) String contrasena,
        @Pattern(regexp = "^[1-9A-D]{4}$") String passwordapp,
        @Pattern(regexp = "^[1-9A-D]{4}$") String passwordpuerta
) {
}
