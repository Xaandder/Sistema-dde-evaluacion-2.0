package com.semillero.evaluacion.solicitudes.dto;

import java.time.LocalDateTime;

/**
 * Respuesta de una solicitud de vinculacion con sus trazas de resolucion.
 */
public record SolicitudRespuesta(
        Long id,
        Long acudienteId,
        Long estudianteId,
        String estado,
        LocalDateTime fechaCreacion,
        Long revisadoPor,
        LocalDateTime fechaResolucion,
        String observacion) {
}
