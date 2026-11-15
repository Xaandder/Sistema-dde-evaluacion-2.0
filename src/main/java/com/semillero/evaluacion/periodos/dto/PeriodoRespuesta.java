package com.semillero.evaluacion.periodos.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Respuesta de un periodo academico. */
public record PeriodoRespuesta(
        Long id,
        Long anioId,
        Integer numero,
        String nombre,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String estado,
        LocalDateTime creadoEn) {
}
