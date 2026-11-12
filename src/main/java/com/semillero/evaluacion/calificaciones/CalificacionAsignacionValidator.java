package com.semillero.evaluacion.calificaciones;

import java.util.Objects;

/** Valida que una calificación conserve la asignación con la que fue creada. */
public final class CalificacionAsignacionValidator {

    private CalificacionAsignacionValidator() {
    }

    public static void validarSinCambios(Asignacion original, Asignacion propuesta) {
        Objects.requireNonNull(original, "La asignación original es obligatoria");
        Objects.requireNonNull(propuesta, "La asignación propuesta es obligatoria");

        if (!original.equals(propuesta)) {
            throw new IllegalArgumentException(
                    "No se puede cambiar la asignación original de una calificación.");
        }
    }

    public record Asignacion(
            Long asignacionId,
            Long materiaId,
            Long grupoId,
            Long anioId,
            Long periodoId) {
    }
}