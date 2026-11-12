package com.semillero.evaluacion.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/v1/auth/cambiar-contrasena. La politica completa
 * (letras y numeros, distinta a la actual) se valida en el servicio para dar
 * mensajes claros.
 */
public record CambioContrasenaPeticion(
        @NotBlank(message = "La contrasena actual es obligatoria") String contrasenaActual,
        @NotBlank(message = "La nueva contrasena es obligatoria")
        @Size(min = 8, max = 100, message = "La nueva contrasena debe tener entre 8 y 100 caracteres")
        String contrasenaNueva,
        @NotBlank(message = "La confirmacion es obligatoria") String confirmacion) {
}
