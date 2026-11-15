package com.semillero.evaluacion.matriculas.dto;

import java.time.LocalDateTime;

/** Respuesta de una matricula (incluye el anio numerico para lectura directa). */
public record MatriculaRespuesta(
        Long id,
        Long estudianteId,
        Long grupoId,
        Long anioId,
        Integer anio,
        LocalDateTime fechaMatricula,
        String estado) {
}
