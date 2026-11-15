package com.semillero.evaluacion.anios.dto;

import java.time.LocalDateTime;

/** Respuesta de un anio lectivo. */
public record AnioRespuesta(
        Long id,
        Integer anio,
        String estado,
        LocalDateTime creadoEn) {
}
