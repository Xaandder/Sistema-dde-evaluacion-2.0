package com.semillero.evaluacion.grupos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Peticion para modificar un grupo: nombre y descripcion. El anio al que
 * pertenece queda fijo desde el alta y el estado cambia con
 * activar/desactivar.
 */
public record ActualizarGrupoPeticion(
        @NotBlank(message = "El nombre del grupo es obligatorio")
        @Size(max = 20, message = "El nombre no puede superar 20 caracteres")
        String nombre,

        @Size(max = 200, message = "La descripcion no puede superar 200 caracteres")
        String descripcion) {
}
