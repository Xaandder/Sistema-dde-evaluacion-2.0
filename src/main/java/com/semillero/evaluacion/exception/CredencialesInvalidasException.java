package com.semillero.evaluacion.exception;

/** Usuario no existe o contrasena incorrecta (mensaje deliberadamente igual para ambos). */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Credenciales invalidas");
    }
}
