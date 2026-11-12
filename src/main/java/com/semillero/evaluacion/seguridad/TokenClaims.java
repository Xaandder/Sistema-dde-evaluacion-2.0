package com.semillero.evaluacion.seguridad;

import java.time.Instant;

/**
 * Contenido verificado de un token JWT valido.
 *
 * @param usuario                   claim "sub": nombre de cuenta
 * @param usuarioId                 claim "uid": ID en la tabla USUARIOS
 * @param rol                       claim "rol": rol del sistema
 * @param cambioContrasenaPendiente claim "pend": contrasena temporal sin cambiar
 * @param expiracion                claim "exp": momento de vencimiento
 */
public record TokenClaims(
        String usuario,
        Long usuarioId,
        String rol,
        boolean cambioContrasenaPendiente,
        Instant expiracion) {
}
