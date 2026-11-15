package com.semillero.evaluacion.calificaciones.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Peticion para registrar una nota. El cliente SOLO indica la matricula, la
 * asignacion y el numero de evaluacion: el estudiante se deriva de la
 * matricula y la materia/grupo/anio/periodo de la asignacion (la base lo
 * ancla con las FKs compuestas FK_CAL_MATRICULA y FK_CAL_ASIGNACION).
 *
 * <p>Reglas repetidas de la base: NUMERO_EVALUACION 1..10
 * (CK_CALIF_NUMERO), NOTA 0.0..5.0 con dos decimales (CK_CALIF_NOTA,
 * NUMBER(4,2)) y COMENTARIO hasta 500 caracteres.</p>
 */
public record CrearCalificacionPeticion(
        @NotNull(message = "La matricula es obligatoria")
        Long matriculaId,

        @NotNull(message = "La asignacion es obligatoria")
        Long asignacionId,

        @NotNull(message = "El numero de evaluacion es obligatorio")
        @Min(value = 1, message = "El numero de evaluacion va de 1 a 10")
        @Max(value = 10, message = "El numero de evaluacion va de 1 a 10")
        Integer numeroEvaluacion,

        @NotNull(message = "La nota es obligatoria")
        @DecimalMin(value = "0.0", message = "La nota va de 0.0 a 5.0")
        @DecimalMax(value = "5.0", message = "La nota va de 0.0 a 5.0")
        @Digits(integer = 1, fraction = 2, message = "La nota usa maximo un entero y dos decimales")
        BigDecimal nota,

        @Size(max = 500, message = "El comentario no puede superar 500 caracteres")
        String comentario) {
}
