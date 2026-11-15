package com.semillero.evaluacion.matriculas.dto;

import jakarta.validation.constraints.NotNull;

/** Peticion para matricular a un estudiante en un grupo. El anio se deriva del grupo. */
public record CrearMatriculaPeticion(
        @NotNull(message = "El estudiante es obligatorio")
        Long estudianteId,

        @NotNull(message = "El grupo es obligatorio")
        Long grupoId) {
}
