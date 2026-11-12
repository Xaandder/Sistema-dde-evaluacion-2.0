package com.semillero.evaluacion.calificaciones;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CalificacionAsignacionValidatorTests {

    @Test
    @DisplayName("Permite conservar los cinco identificadores originales")
    void permiteLaMismaAsignacion() {
        var asignacion = new CalificacionAsignacionValidator.Asignacion(1L, 2L, 3L, 4L, 5L);

        assertDoesNotThrow(() ->
                CalificacionAsignacionValidator.validarSinCambios(asignacion, asignacion));
    }

    @Test
    @DisplayName("Rechaza cambios en cualquiera de los identificadores de asignación")
    void rechazaCambiosEnCadaIdentificador() {
        var original = new CalificacionAsignacionValidator.Asignacion(1L, 2L, 3L, 4L, 5L);
        var propuestas = new CalificacionAsignacionValidator.Asignacion[] {
                new CalificacionAsignacionValidator.Asignacion(9L, 2L, 3L, 4L, 5L),
                new CalificacionAsignacionValidator.Asignacion(1L, 9L, 3L, 4L, 5L),
                new CalificacionAsignacionValidator.Asignacion(1L, 2L, 9L, 4L, 5L),
                new CalificacionAsignacionValidator.Asignacion(1L, 2L, 3L, 9L, 5L),
                new CalificacionAsignacionValidator.Asignacion(1L, 2L, 3L, 4L, 9L)
        };

        for (var propuesta : propuestas) {
            assertThrows(IllegalArgumentException.class,
                    () -> CalificacionAsignacionValidator.validarSinCambios(original, propuesta));
        }
    }
}