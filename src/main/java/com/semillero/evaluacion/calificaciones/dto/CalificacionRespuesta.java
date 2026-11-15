package com.semillero.evaluacion.calificaciones.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Respuesta de una calificacion con TODOS los identificadores derivados que
 * la base copia en la fila (estudiante de la matricula; materia, grupo,
 * anio y periodo de la asignacion), mas las trazas de registro y edicion.
 */
public record CalificacionRespuesta(
        Long id,
        Long matriculaId,
        Long estudianteId,
        Long asignacionId,
        Long materiaId,
        Long grupoId,
        Long anioId,
        Long periodoId,
        Integer numeroEvaluacion,
        BigDecimal nota,
        String comentario,
        LocalDateTime fechaRegistro,
        Long registradoPor,
        LocalDateTime fechaActualizacion,
        Long actualizadoPor) {
}
