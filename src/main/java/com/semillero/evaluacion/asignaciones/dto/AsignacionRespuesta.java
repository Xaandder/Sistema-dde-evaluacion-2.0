package com.semillero.evaluacion.asignaciones.dto;

import java.time.LocalDateTime;

/**
 * Respuesta de una asignacion docente (incluye el anio numerico derivado
 * para lectura directa).
 */
public record AsignacionRespuesta(
        Long id,
        Long profesorId,
        Long materiaId,
        Long grupoId,
        Long periodoId,
        Long anioId,
        Integer anio,
        String estado,
        LocalDateTime creadoEn) {
}
