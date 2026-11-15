package com.semillero.evaluacion.grupos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Peticion para crear un grupo academico de un anio lectivo. */
public record CrearGrupoPeticion(
        @NotNull(message = "El anio lectivo es obligatorio")
        Long anioId,

        @NotBlank(message = "El nombre del grupo es obligatorio")
        @Size(max = 20, message = "El nombre no puede superar 20 caracteres")
        String nombre,

        @Size(max = 200, message = "La descripcion no puede superar 200 caracteres")
        String descripcion) {
}
