package com.semillero.evaluacion.acudientes.dto;

import java.time.LocalDateTime;

/**
 * Respuesta de un acudiente (datos personales y de la cuenta).
 */
public record AcudienteRespuesta(
        Long id,
        Long usuarioId,
        String usuario,
        String documento,
        String nombres,
        String apellidos,
        String parentesco,
        String correo,
        String telefono,
        String estado,
        LocalDateTime creadoEn) {
}
