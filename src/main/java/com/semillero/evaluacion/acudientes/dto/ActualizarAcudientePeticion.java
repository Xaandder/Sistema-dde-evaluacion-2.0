package com.semillero.evaluacion.acudientes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Peticion para actualizar los datos personales de un acudiente. El
 * documento se puede cambiar respetando su unicidad (UNQ_ACUD_DOCUMENTO).
 */
public record ActualizarAcudientePeticion(
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
