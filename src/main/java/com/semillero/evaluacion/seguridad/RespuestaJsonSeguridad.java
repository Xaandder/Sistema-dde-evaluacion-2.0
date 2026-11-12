package com.semillero.evaluacion.seguridad;

import com.semillero.evaluacion.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * Escribe los errores 401/403 producidos ANTES de llegar a un controlador
 * (sin token, token invalido, rol insuficiente) en el mismo formato
 * {@link ApiError} que produce el GlobalExceptionHandler, para que toda la
 * API tenga respuestas uniformes.
 */
public class RespuestaJsonSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper json;

    public RespuestaJsonSeguridad(ObjectMapper json) {
        this.json = json;
    }

    /** Sin token o token invalido en una ruta protegida. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException ex) {
        escribir(request, response, HttpStatus.UNAUTHORIZED, "No autenticado");
    }

    /** Token valido pero rol insuficiente para la ruta. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException ex) {
        escribir(request, response, HttpStatus.FORBIDDEN, "Acceso denegado: no tiene el rol requerido");
    }

    /** 403 usado por el filtro cuando la contrasena temporal aun no se cambio. */
    public void escribir(HttpServletResponse response, int estado, String mensaje) {
        escribir(null, response, HttpStatus.valueOf(estado), mensaje);
    }

    private void escribir(HttpServletRequest request, HttpServletResponse response,
            HttpStatus estado, String mensaje) {
        try {
            String ruta = request != null ? request.getRequestURI() : "";
            ApiError cuerpo = new ApiError(
                    Instant.now(),
                    estado.value(),
                    estado.getReasonPhrase(),
                    mensaje,
                    ruta,
                    null);
            response.setStatus(estado.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(json.writeValueAsString(cuerpo));
        } catch (Exception e) {
            // Ultima linea de defensa: nunca dejar la respuesta sin estado.
            response.setStatus(estado.value());
        }
    }
}
