package com.semillero.evaluacion.acudientes;

import com.semillero.evaluacion.acudientes.dto.ActualizarAcudientePeticion;
import com.semillero.evaluacion.acudientes.dto.AcudienteRespuesta;
import com.semillero.evaluacion.acudientes.dto.CrearAcudientePeticion;
import com.semillero.evaluacion.usuarios.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de acudientes (/api/v1/acudientes).
 *
 * <p>Permisos: el ADMINISTRADOR gestiona todo el ciclo (crear con cuenta y
 * contrasena temporal, listar, actualizar, activar y desactivar). El rol
 * ACUDIENTE solo puede consultar SU perfil ({@code /perfil}); la
 * administracion de cuentas evita un registro publico de acudientes, que
 * quedaria como flujo del DDL pendiente de una decision de seguridad.</p>
 */
@RestController
@RequestMapping("/api/v1/acudientes")
public class AcudienteController {

    private final AcudienteServicio servicio;

    public AcudienteController(AcudienteServicio servicio) {
        this.servicio = servicio;
    }

    /** Registra un acudiente con su cuenta (solo ADMINISTRADOR). */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<AcudienteRespuesta> crear(
            @Valid @RequestBody CrearAcudientePeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        AcudienteRespuesta respuesta = servicio.crear(peticion,
                idUsuario(sesion), request.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/v1/acudientes/" + respuesta.id()))
                .body(respuesta);
    }

    /** Consulta acudientes; con ?estado= filtra por ACTIVO o INACTIVO (solo ADMIN). */
    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<AcudienteRespuesta> listar(
            @RequestParam(name = "estado", required = false) String estado) {
        return servicio.listar(estado);
    }

    /**
     * Consulta un acudiente por id: ADMIN o el propio acudiente (un
     * acudiente que consulta el perfil ajeno recibe 403).
     */
    @GetMapping("/{id}")
    public AcudienteRespuesta buscarPorId(@PathVariable Long id, Authentication sesion) {
        return servicio.buscarPorId(id, idUsuario(sesion), esAdmin(sesion));
    }

    /** Perfil del acudiente autenticado (rol ACUDIENTE). */
    @GetMapping("/perfil")
    @PreAuthorize("hasRole('ACUDIENTE')")
    public AcudienteRespuesta perfil(Authentication sesion) {
        return servicio.perfilDeLaSesion(idUsuario(sesion));
    }

    /** Actualiza los datos personales (solo ADMINISTRADOR). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public AcudienteRespuesta actualizar(@PathVariable Long id,
            @Valid @RequestBody ActualizarAcudientePeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        return servicio.actualizar(id, peticion, idUsuario(sesion), request.getRemoteAddr());
    }

    /** Desactiva el acudiente y su cuenta (solo ADMINISTRADOR). */
    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public AcudienteRespuesta desactivar(@PathVariable Long id,
            Authentication sesion, HttpServletRequest request) {
        return servicio.desactivar(id, idUsuario(sesion), request.getRemoteAddr());
    }

    /** Reactiva el acudiente y su cuenta (solo ADMINISTRADOR). */
    @PostMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public AcudienteRespuesta activar(@PathVariable Long id,
            Authentication sesion, HttpServletRequest request) {
        return servicio.activar(id, idUsuario(sesion), request.getRemoteAddr());
    }

    private Long idUsuario(Authentication sesion) {
        return ((Usuario) sesion.getPrincipal()).getId();
    }

    private boolean esAdmin(Authentication sesion) {
        return sesion.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));
    }
}
