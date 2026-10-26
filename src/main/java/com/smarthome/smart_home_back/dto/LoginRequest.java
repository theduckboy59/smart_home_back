package com.smarthome.smart_home_back.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        @NotBlank(message = "La contrasena es obligatoria")
        String contrasena
) {
}