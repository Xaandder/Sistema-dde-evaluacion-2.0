package com.semillero.evaluacion.solicitudes.dto;

import jakarta.validation.constraints.Size;

/**
 * Peticion del ADMINISTRADOR para resolver (aprobar o rechazar) una
 * solicitud PENDIENTE. La observacion queda como trazabilidad.
 */
public record ResolucionSolicitudPeticion(
        @Size(max = 500, message = "La observacion no puede superar 500 caracteres")
        String observacion) {
}
