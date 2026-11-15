package com.semillero.evaluacion.periodos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Peticion para registrar un periodo academico. El estado no se recibe: todo
 * periodo nuevo nace PENDIENTE (ciclo de vida del sistema).
 */
public record CrearPeriodoPeticion(
        @NotNull(message = "El anio lectivo es obligatorio")
        Long anioId,

        @NotNull(message = "El numero del periodo es obligatorio")
        @Min(value = 1, message = "El numero del periodo debe ser mayor o igual a 1")
        @Max(value = 99, message = "El numero del periodo no cabe en la base (2 digitos)")
        Integer numero,

        @NotBlank(message = "El nombre del periodo es obligatorio")
        @Size(max = 50, message = "El nombre no puede superar 50 caracteres")
        String nombre,

        LocalDate fechaInicio,

        LocalDate fechaFin) {
}
