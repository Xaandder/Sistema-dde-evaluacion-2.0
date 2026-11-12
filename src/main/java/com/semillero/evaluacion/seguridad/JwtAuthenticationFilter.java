package com.semillero.evaluacion.seguridad;

import com.semillero.evaluacion.usuarios.Usuario;
import com.semillero.evaluacion.usuarios.UsuarioRepositorio;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Filtro de autenticacion Bearer JWT.
 *
 * <p>Sin encabezado Authorization deja pasar la peticion (rutas publicas o
 * el punto de entrada que respondera 401). Con token: valida firma/vigencia,
 * verifica el cambio obligatorio de contrasena y recarga el usuario desde la
 * base en CADA peticion (decision aprobada: un usuario desactivado pierde
 * acceso de inmediato aunque su token siga vigente).</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Unica ruta permitida mientras CAMBIO_CONTRASENA_PENDIENTE sea verdadero. */
    private static final String RUTA_CAMBIO = "/api/v1/auth/cambiar-contrasena";

    private final JwtService jwtService;
    /** Resolucion perezosa del repositorio: en el perfil de pruebas JPA no existe. */
    private final ObjectProvider<UsuarioRepositorio> repositorio;
    private final RespuestaJsonSeguridad respuestas;
    private final ObjectMapper json;

    public JwtAuthenticationFilter(JwtService jwtService,
            ObjectProvider<UsuarioRepositorio> repositorio,
            ObjectMapper json) {
        this.jwtService = jwtService;
        this.repositorio = repositorio;
        this.respuestas = new RespuestaJsonSeguridad(json);
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain cadena) throws ServletException, IOException {
        String encabezado = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (encabezado == null || !encabezado.startsWith("Bearer ")) {
            cadena.doFilter(request, response);
            return;
        }

        TokenClaims claims;
        try {
            claims = jwtService.validar(encabezado.substring(7).trim());
        } catch (JwtInvalidoException e) {
            respuestas.escribir(response, 401, "Token invalido o expirado");
            return;
        }

        // Cambio obligatorio de contrasena: bloquea todo lo demas.
        if (claims.cambioContrasenaPendiente()
                && !RUTA_CAMBIO.equals(request.getRequestURI())) {
            respuestas.escribir(response, 403,
                    "Debe cambiar su contrasena temporal antes de usar el sistema");
            return;
        }

        Usuario usuario = repositorio.getObject().findByUsuario(claims.usuario()).orElse(null);
        if (usuario == null || !Boolean.TRUE.equals(usuario.getActivo())) {
            respuestas.escribir(response, 401, "Cuenta no encontrada o inactiva");
            return;
        }
        // El estado pudo cambiar desde la emision del token (flag desactualizado).
        if (Boolean.TRUE.equals(usuario.getCambioContrasenaPendiente())
                != claims.cambioContrasenaPendiente()) {
            respuestas.escribir(response, 401, "Token desactualizado: inicie sesion de nuevo");
            return;
        }

        UsernamePasswordAuthenticationToken autenticacion = new UsernamePasswordAuthenticationToken(
                usuario,
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name())));
        SecurityContextHolder.getContext().setAuthentication(autenticacion);

        cadena.doFilter(request, response);
    }
}
