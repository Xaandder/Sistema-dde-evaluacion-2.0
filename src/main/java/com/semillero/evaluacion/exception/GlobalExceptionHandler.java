package com.semillero.evaluacion.exception;

import com.semillero.evaluacion.exception.CredencialesInvalidasException;
import com.semillero.evaluacion.exception.CuentaInactivaException;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.exception.ReglaNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Manejo centralizado de errores para toda la API.
 *
 * <p>Cualquier excepcion no controlada en un controlador termina aqui,
 * evitando que se muestre al cliente el stack trace interno del servidor.
 * Cada {@code @ExceptionHandler} convierte un tipo de error en un codigo
 * HTTP y un cuerpo {@link ApiError} uniforme.</p>
 *
 * <p>En las Etapas 2 y 3 se agregaran aqui los manejadores de:
 * recurso no encontrado (404), regla de negocio (409/422), acceso denegado
 * (403) y no autenticado (401).</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Datos de entrada invalidos (anotaciones @Valid en DTOs). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> manejarArgumentosInvalidos(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(campo -> campo.getField() + ": " + campo.getDefaultMessage())
                .toList();
        return construir(HttpStatus.BAD_REQUEST, "Datos de entrada invalidos", request, detalles);
    }

    /** Datos de entrada invalidos (validacion directa en parametros del controlador). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> manejarValidacionDeParametros(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "Parametros de entrada invalidos", request, null);
    }

    /** El JSON enviado no se puede leer o no es valido. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> manejarJsonNoLegible(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "El cuerpo de la peticion no es JSON valido", request, null);
    }

    /** Un parametro de ruta o query no tiene el tipo esperado (ej. texto donde va un numero). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> manejarTipoIncorrecto(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "Un parametro tiene un tipo de dato incorrecto", request, null);
    }

    /** Credenciales invalidas (login o cambio de contrasena). */
    @ExceptionHandler(CredencialesInvalidasException.class)
    ResponseEntity<ApiError> manejarCredencialesInvalidas(
            CredencialesInvalidasException ex, HttpServletRequest request) {
        return construir(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, null);
    }

    /** Cuenta desactivada (ACTIVO = 0). */
    @ExceptionHandler(CuentaInactivaException.class)
    ResponseEntity<ApiError> manejarCuentaInactiva(
            CuentaInactivaException ex, HttpServletRequest request) {
        return construir(HttpStatus.FORBIDDEN, ex.getMessage(), request, null);
    }

    /** Violacion de regla de negocio con mensaje para el cliente. */
    @ExceptionHandler(ReglaNegocioException.class)
    ResponseEntity<ApiError> manejarReglaNegocio(
            ReglaNegocioException ex, HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    /** Recurso solicitado inexistente (ids de rutas que no estan en la base). */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    ResponseEntity<ApiError> manejarRecursoNoEncontrado(
            RecursoNoEncontradoException ex, HttpServletRequest request) {
        return construir(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    /** Conflicto con el estado del recurso (p. ej., dato unico ya ocupado). */
    @ExceptionHandler(ConflictoEstadoException.class)
    ResponseEntity<ApiError> manejarConflictoEstado(
            ConflictoEstadoException ex, HttpServletRequest request) {
        return construir(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    /**
     * Argumento invalido en una regla interna (p. ej., fila de calificacion
     * inconsistente con su asignacion). Mensaje controlado por el codigo del
     * sistema; nunca se exponen detalles internos de Java.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> manejarArgumentoIlegal(
            IllegalArgumentException ex, HttpServletRequest request) {
        String mensaje = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "Datos invalidos en la operacion";
        return construir(HttpStatus.BAD_REQUEST, mensaje, request, null);
    }

    /** Token valido pero rol insuficiente (seguridad de metodo). */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> manejarAccesoDenegado(
            AccessDeniedException ex, HttpServletRequest request) {
        return construir(HttpStatus.FORBIDDEN, "Acceso denegado: no tiene el rol requerido", request, null);
    }

    /** Ruta inexistente en la API. */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> manejarRutaInexistente(
            NoResourceFoundException ex, HttpServletRequest request) {
        return construir(HttpStatus.NOT_FOUND, "Ruta no encontrada", request, null);
    }

    /** Error inesperado del servidor: no se exponen detalles internos al cliente. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> manejarErrorInesperado(Exception ex, HttpServletRequest request) {
        log.error("Error inesperado en {} {}: {}", request.getMethod(), request.getRequestURI(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", request, null);
    }

    private ResponseEntity<ApiError> construir(
            HttpStatus estado, String mensaje, HttpServletRequest request, List<String> detalles) {
        ApiError cuerpo = new ApiError(
                Instant.now(),
                estado.value(),
                estado.getReasonPhrase(),
                mensaje,
                request.getRequestURI(),
                detalles);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
