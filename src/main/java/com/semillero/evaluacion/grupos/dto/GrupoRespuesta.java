package com.semillero.evaluacion.grupos.dto;

import java.time.LocalDateTime;

/** Respuesta de un grupo academico. */
public record GrupoRespuesta(
        Long id,
        Long anioId,
        String nombre,
        String descripcion,
        String estado,
        LocalDateTime creadoEn) {
}
