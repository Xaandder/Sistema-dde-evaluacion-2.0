package com.semillero.evaluacion.calificaciones.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Peticion para editar una nota existente. Solo NOTA y COMENTARIO son
 * editables: la matricula, la asignacion y el numero de evaluacion quedan
 * fijos desde el alta (la asignacion es inmutable y la valida
 * {@link com.semillero.evaluacion.calificaciones.CalificacionAsignacionValidator}).
 */
public record ActualizarCalificacionPeticion(
        @NotNull(message = "La nota es obligatoria")
        @DecimalMin(value = "0.0", message = "La nota va de 0.0 a 5.0")
        @DecimalMax(value = "5.0", message = "La nota va de 0.0 a 5.0")
        @Digits(integer = 1, fraction = 2, message = "La nota usa maximo un entero y dos decimales")
        BigDecimal nota,

        @Size(max = 500, message = "El comentario no puede superar 500 caracteres")
        String comentario) {
}
