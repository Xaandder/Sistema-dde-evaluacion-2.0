package com.semillero.evaluacion.asignaciones.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Peticion para crear una asignacion docente. El anio NO se recibe: se
 * deriva del grupo y del periodo (que deben coincidir, FKs compuestas de
 * la base) y el estado nace ACTIVA.
 */
public record CrearAsignacionPeticion(
        @NotNull(message = "El profesor es obligatorio")
        Long profesorId,

        @NotNull(message = "La materia es obligatoria")
        Long materiaId,

        @NotNull(message = "El grupo es obligatorio")
        Long grupoId,

        @NotNull(message = "El periodo es obligatorio")
        Long periodoId) {
}
