package com.semillero.evaluacion.vinculaciones.dto;

import java.time.LocalDateTime;

/**
 * Informacion del estudiante que ve un ACUDIENTE con vinculacion
 * autorizada: datos basicos, sin la cuenta de acceso ni detalles
 * administrativos. Un acudiente JAMAS recibe datos de estudiantes ajenos.
 */
public record EstudianteVinculadoRespuesta(
        Long estudianteId,
        String documento,
        String nombres,
        String apellidos,
        String estado,
        LocalDateTime fechaVinculacion) {
}
