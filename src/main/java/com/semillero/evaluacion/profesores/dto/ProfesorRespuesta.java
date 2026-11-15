package com.semillero.evaluacion.profesores.dto;

import java.time.LocalDateTime;

/** Respuesta de un profesor (incluye el nombre de su cuenta de acceso). */
public record ProfesorRespuesta(
        Long id,
        Long usuarioId,
        String usuario,
        String documento,
        String nombres,
        String apellidos,
        String especialidad,
        String correo,
        String telefono,
        String estado,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn) {
}
