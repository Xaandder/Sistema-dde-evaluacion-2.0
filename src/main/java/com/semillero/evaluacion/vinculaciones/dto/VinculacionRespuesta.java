package com.semillero.evaluacion.vinculaciones.dto;

import java.time.LocalDateTime;

/**
 * Respuesta de una vinculacion estudiante-acudiente.
 */
public record VinculacionRespuesta(
        Long estudianteId,
        Long acudienteId,
        LocalDateTime fechaVinculacion) {
}
