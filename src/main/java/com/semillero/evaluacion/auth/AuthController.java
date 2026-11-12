package com.semillero.evaluacion.auth;

import com.semillero.evaluacion.auth.dto.CambioContrasenaPeticion;
import com.semillero.evaluacion.auth.dto.LoginPeticion;
import com.semillero.evaluacion.auth.dto.LoginRespuesta;
import com.semillero.evaluacion.auth.dto.MiUsuarioRespuesta;
import com.semillero.evaluacion.usuarios.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticacion (Etapa 3).
 *
 * <p>El filtro de seguridad entrega el usuario recargado desde la base como
 * principal de la Authentication en cada peticion protegida.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Login publico (permitAll en SecurityConfig). */
    @PostMapping("/login")
    public LoginRespuesta login(@Valid @RequestBody LoginPeticion peticion,
            HttpServletRequest request) {
        return authService.login(peticion.usuario(), peticion.contrasena(),
                request.getRemoteAddr());
    }

    /** Cambio de contrasena: exige token valido (la unica ruta utilizable con pend=1). */
    @PostMapping("/cambiar-contrasena")
    public LoginRespuesta cambiarContrasena(@Valid @RequestBody CambioContrasenaPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        Usuario usuario = (Usuario) sesion.getPrincipal();
        return authService.cambiarContrasena(
                usuario.getId(),
                peticion.contrasenaActual(),
                peticion.contrasenaNueva(),
                peticion.confirmacion(),
                request.getRemoteAddr());
    }

    /** Datos de la cuenta del token (recargados desde la base por el filtro). */
    @GetMapping("/me")
    public MiUsuarioRespuesta me(Authentication sesion) {
        Usuario usuario = (Usuario) sesion.getPrincipal();
        return new MiUsuarioRespuesta(
                usuario.getId(),
                usuario.getUsuario(),
                usuario.getRol().name(),
                Boolean.TRUE.equals(usuario.getCambioContrasenaPendiente()));
    }
}
