package com.semillero.evaluacion.seguridad;

/** Un token JWT no es valido (mal formado, firma incorrecta o expirado). */
public class JwtInvalidoException extends RuntimeException {

    public JwtInvalidoException(String mensaje) {
        super(mensaje);
    }
}
