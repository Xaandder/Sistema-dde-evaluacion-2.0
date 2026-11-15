package com.semillero.evaluacion.estudiantes.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Respuesta de un estudiante (incluye el nombre de su cuenta de acceso). */
public record EstudianteRespuesta(
        Long id,
        Long usuarioId,
        String usuario,
        String documento,
        String nombres,
        String apellidos,
        LocalDate fechaNacimiento,
        String correo,
        String telefono,
        String estado,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn) {
}
