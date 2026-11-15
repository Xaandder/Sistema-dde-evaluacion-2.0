package com.semillero.evaluacion.anios.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Peticion para registrar un anio lectivo. El estado no se recibe: todo
 * anio nuevo nace ACTIVO (DEFAULT de la base, CK_ANIOS_ESTADO).
 */
public record CrearAnioPeticion(
        @NotNull(message = "El anio es obligatorio")
        @Min(value = 2000, message = "El anio no puede ser menor a 2000")
        @Max(value = 2100, message = "El anio no puede ser mayor a 2100")
        Integer anio) {
}
