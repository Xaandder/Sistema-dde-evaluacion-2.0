package com.semillero.evaluacion.exception;

/** Violacion de una regla de negocio con mensaje entendible para el cliente (HTTP 400). */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
