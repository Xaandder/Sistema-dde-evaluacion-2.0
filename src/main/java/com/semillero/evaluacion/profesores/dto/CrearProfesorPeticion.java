package com.semillero.evaluacion.profesores.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Peticion para registrar un profesor: crea la cuenta (rol PROFESOR,
 * contrasena temporal con cambio obligatorio) y sus datos personales en una
 * sola transaccion.
 */
public record CrearProfesorPeticion(
        @NotBlank(message = "El usuario de acceso es obligatorio")
        @Size(max = 100, message = "El usuario de acceso no puede superar 100 caracteres")
        String usuario,

        @NotBlank(message = "La contrasena temporal es obligatoria")
        String contrasenaTemporal,

        @NotBlank(message = "El documento es obligatorio")
        @Size(max = 20, message = "El documento no puede superar 20 caracteres")
        String documento,

        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 100, message = "Los nombres no pueden superar 100 caracteres")
        String nombres,

        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 100, message = "Los apellidos no pueden superar 100 caracteres")
        String apellidos,

        @Size(max = 100, message = "La especialidad no puede superar 100 caracteres")
        String especialidad,

        @Size(max = 150, message = "El correo no puede superar 150 caracteres")
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "El correo no tiene un formato valido")
        String correo,

        @Size(max = 30, message = "El telefono no puede superar 30 caracteres")
        String telefono,

        LocalDate fechaNacimiento) {
}
