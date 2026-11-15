package com.semillero.evaluacion.estudiantes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Peticion para actualizar los datos personales de un estudiante. La cuenta
 * (usuario_id) no se modifica y el estado cambia con activar/desactivar.
 */
public record ActualizarEstudiantePeticion(
        @NotBlank(message = "El documento es obligatorio")
        @Size(max = 20, message = "El documento no puede superar 20 caracteres")
        String documento,

        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 100, message = "Los nombres no pueden superar 100 caracteres")
        String nombres,

        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 100, message = "Los apellidos no pueden superar 100 caracteres")
        String apellidos,

        LocalDate fechaNacimiento,

        @Size(max = 150, message = "El correo no puede superar 150 caracteres")
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "El correo no tiene un formato valido")
        String correo,

        @Size(max = 30, message = "El telefono no puede superar 30 caracteres")
        String telefono) {
}
