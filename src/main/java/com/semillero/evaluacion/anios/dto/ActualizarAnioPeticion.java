package com.semillero.evaluacion.anios.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Peticion para modificar la informacion de un anio lectivo (unicamente
 * su numero; el estado se cambia con las acciones activar/cerrar).
 */
public record ActualizarAnioPeticion(
        @NotNull(message = "El anio es obligatorio")
        @Min(value = 2000, message = "El anio no puede ser menor a 2000")
        @Max(value = 2100, message = "El anio no puede ser mayor a 2100")
        Integer anio) {
}
