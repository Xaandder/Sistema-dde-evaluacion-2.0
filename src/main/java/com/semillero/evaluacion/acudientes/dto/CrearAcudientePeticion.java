package com.semillero.evaluacion.acudientes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Peticion para registrar un acudiente: crea la cuenta (rol ACUDIENTE,
 * contrasena temporal con cambio obligatorio) y sus datos personales en una
 * sola transaccion. El correo es obligatorio porque la vinculacion con los
 * estudiantes se comunica por correo (diseño del DDL, tabla ACUDIENTES).
 */
public record CrearAcudientePeticion(
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

        @Size(max = 30, message = "El parentesco no puede superar 30 caracteres")
        String parentesco,

        @NotBlank(message = "El correo es obligatorio")
        @Size(max = 150, message = "El correo no puede superar 150 caracteres")
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "El correo no tiene un formato valido")
        String correo,

        @Size(max = 30, message = "El telefono no puede superar 30 caracteres")
        String telefono) {
}
