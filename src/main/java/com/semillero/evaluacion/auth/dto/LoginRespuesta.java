package com.semillero.evaluacion.auth.dto;

/**
 * Respuesta de login (y de cambio de contrasena, que entrega un token nuevo).
 *
 * @param token                     JWT firmado
 * @param usuario                   nombre de cuenta
 * @param rol                       rol del sistema
 * @param cambioContrasenaPendiente true si el token solo sirve para cambiar la
 *                                  contrasena (primer inicio de sesion)
 */
public record LoginRespuesta(
        String token,
        String usuario,
        String rol,
        boolean cambioContrasenaPendiente) {
}
