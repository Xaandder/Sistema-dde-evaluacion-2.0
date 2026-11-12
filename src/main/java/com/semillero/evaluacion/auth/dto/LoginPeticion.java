package com.semillero.evaluacion.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de POST /api/v1/auth/login. */
public record LoginPeticion(
        @NotBlank(message = "El usuario es obligatorio") String usuario,
        @NotBlank(message = "La contrasena es obligatoria") String contrasena) {
}
