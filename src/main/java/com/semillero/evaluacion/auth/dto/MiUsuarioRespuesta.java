package com.semillero.evaluacion.auth.dto;

/** Respuesta de GET /api/v1/auth/me. */
public record MiUsuarioRespuesta(
        Long id,
        String usuario,
        String rol,
        boolean cambioContrasenaPendiente) {
}
