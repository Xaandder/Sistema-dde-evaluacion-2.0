package com.semillero.evaluacion.solicitudes.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Peticion del ACUDIENTE para solicitar la vinculacion con un estudiante.
 * La solicitud nace PENDIENTE y la resuelve el ADMINISTRADOR.
 */
public record CrearSolicitudPeticion(
        @NotNull(message = "El estudiante es obligatorio")
        Long estudianteId,

        @Size(max = 500, message = "La observacion no puede superar 500 caracteres")
        String observacion) {
}
