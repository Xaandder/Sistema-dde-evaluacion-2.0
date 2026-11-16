package com.semillero.evaluacion.exception;

/**
 * Conflicto con el estado actual de un recurso (HTTP 409): la operacion es
 * sintacticamente valida pero choca con datos que ya existen o con un
 * estado que lo impide (p. ej., un espacio de evaluacion ya ocupado por la
 * UNIQUE de CALIFICACIONES).
 *
 * <p>Extiende {@link ReglaNegocioException} para que todo el codigo y las
 * pruebas que tratan reglas de negocio de forma generica sigan
 * funcionando; el manejador global la distingue para responder 409.</p>
 */
public class ConflictoEstadoException extends ReglaNegocioException {

    public ConflictoEstadoException(String mensaje) {
        super(mensaje);
    }
}
