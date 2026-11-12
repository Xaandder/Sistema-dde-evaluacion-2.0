package com.semillero.evaluacion.exception;

/** La cuenta existe pero esta desactivada (ACTIVO = 0). */
public class CuentaInactivaException extends RuntimeException {

    public CuentaInactivaException() {
        super("La cuenta esta inactiva. Contacte al administrador");
    }
}
