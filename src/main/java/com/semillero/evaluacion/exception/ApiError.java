package com.semillero.evaluacion.exception;

import java.time.Instant;
import java.util.List;

/**
 * Formato uniforme de errores de la API.
 *
 * <p>Todos los endpoints devuelven este cuerpo JSON cuando ocurre un error,
 * para que cualquier cliente (frontend futuro, Swagger, pruebas) reciba
 * siempre la misma estructura.</p>
 *
 * <p>Los campos nulos no se incluyen en el JSON gracias a la configuracion
 * {@code spring.jackson.default-property-inclusion=non_null}.</p>
 *
 * @param fecha    momento en que ocurrio el error
 * @param estado   codigo HTTP (400, 401, 403, 404, 409, 500...)
 * @param error    nombre corto del tipo de error
 * @param mensaje  descripcion entendible del problema
 * @param ruta     endpoint donde ocurrio
 * @param detalles lista de detalles (por ejemplo, campos invalidos); opcional
 */
public record ApiError(
        Instant fecha,
        int estado,
        String error,
        String mensaje,
        String ruta,
        List<String> detalles) {
}
