package com.smarthome.smart_home_back.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearUsuarioRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 72) String contrasena,
        @NotBlank @Pattern(regexp = "^[1-9A-D]{4}$") String passwordapp,
        @NotBlank @Pattern(regexp = "^[1-9A-D]{4}$") String passwordpuerta
) {
}
