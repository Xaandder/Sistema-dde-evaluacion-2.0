package com.semillero.evaluacion.profesores;

import com.semillero.evaluacion.profesores.dto.ActualizarProfesorPeticion;
import com.semillero.evaluacion.profesores.dto.CrearProfesorPeticion;
import com.semillero.evaluacion.profesores.dto.ProfesorRespuesta;
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
 * Endpoints de profesores (/api/v1/profesores).
 *
 * <p>Permisos: la consulta (los docentes disponibles son los de estado
 * ACTIVO) es para cualquier rol autenticado; el registro, la modificacion,
 * la activacion y la desactivacion son exclusivos del ADMINISTRADOR.</p>
 */
@RestController
@RequestMapping("/api/v1/profesores")
public class ProfesorController {

    private final ProfesorServicio servicio;

    public ProfesorController(ProfesorServicio servicio) {
        this.servicio = servicio;
    }

    /** Registra un profesor con su cuenta (solo ADMINISTRADOR). */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ProfesorRespuesta> crear(
            @Valid @RequestBody CrearProfesorPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        ProfesorRespuesta respuesta = servicio.crear(peticion,
                idUsuario(sesion), request.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/v1/profesores/" + respuesta.id()))
                .body(respuesta);
    }

    /** Consulta profesores; con ?estado=ACTIVO se listan los disponibles. */
    @GetMapping
    public List<ProfesorRespuesta> listar(
            @RequestParam(name = "estado", required = false) String estado) {
        return servicio.listar(estado);
    }

    /** Consulta un profesor por id (cualquier rol autenticado). */
    @GetMapping("/{id}")
    public ProfesorRespuesta buscarPorId(@PathVariable Long id) {
        return servicio.buscarPorId(id);
    }

    /** Actualiza los datos personales (solo ADMINISTRADOR). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ProfesorRespuesta actualizar(@PathVariable Long id,
            @Valid @RequestBody ActualizarProfesorPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        return servicio.actualizar(id, peticion, idUsuario(sesion), request.getRemoteAddr());
    }

    /** Desactiva al profesor y su cuenta (solo ADMINISTRADOR). */
    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ProfesorRespuesta desactivar(@PathVariable Long id,
            Authentication sesion, HttpServletRequest request) {
        return servicio.desactivar(id, idUsuario(sesion), request.getRemoteAddr());
    }

    /** Reactiva al profesor y su cuenta (solo ADMINISTRADOR). */
    @PostMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ProfesorRespuesta activar(@PathVariable Long id,
            Authentication sesion, HttpServletRequest request) {
        return servicio.activar(id, idUsuario(sesion), request.getRemoteAddr());
    }

    /** El filtro de seguridad entrega el Usuario recargado de la base. */
    private Long idUsuario(Authentication sesion) {
        return ((Usuario) sesion.getPrincipal()).getId();
    }
}
