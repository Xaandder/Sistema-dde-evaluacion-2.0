package com.semillero.evaluacion.materias.dto;

import java.time.LocalDateTime;

/** Respuesta de una materia del catalogo. */
public record MateriaRespuesta(
        Long id,
        String codigo,
        String nombre,
        String descripcion,
        String estado,
        LocalDateTime creadoEn) {
}
