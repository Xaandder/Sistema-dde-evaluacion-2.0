package com.semillero.evaluacion.vinculaciones.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Peticion para crear una vinculacion directa estudiante-acudiente
 * (operacion administrativa; el flujo normal del acudiente pasa por una
 * SOLICITUD APROBADA).
 */
public record CrearVinculacionPeticion(
        @NotNull(message = "El estudiante es obligatorio")
        Long estudianteId,

        @NotNull(message = "El acudiente es obligatorio")
        Long acudienteId) {
}
