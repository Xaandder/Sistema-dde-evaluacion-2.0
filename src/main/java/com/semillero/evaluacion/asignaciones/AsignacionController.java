package com.semillero.evaluacion.asignaciones;

import com.semillero.evaluacion.asignaciones.dto.AsignacionRespuesta;
import com.semillero.evaluacion.asignaciones.dto.CrearAsignacionPeticion;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de asignaciones docentes (/api/v1/asignaciones).
 *
 * <p>Permisos: la consulta es para cualquier rol autenticado; la creacion,
 * la finalizacion y el reemplazo son exclusivos del ADMINISTRADOR. El
 * registro de calificaciones (modulo 7) validara que solo el profesor
 * titular de la asignacion escriba notas.</p>
 */
@RestController
@RequestMapping("/api/v1/asignaciones")
public class AsignacionController {

    private final AsignacionServicio servicio;

    public AsignacionController(AsignacionServicio servicio) {
        this.servicio = servicio;
    }

    /** Crea una asignacion docente (solo ADMINISTRADOR). */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<AsignacionRespuesta> crear(
            @Valid @RequestBody CrearAsignacionPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        AsignacionRespuesta respuesta = servicio.crear(peticion,
                idUsuario(sesion), request.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/v1/asignaciones/" + respuesta.id()))
                .body(respuesta);
    }

    /** Consulta asignaciones; filtros combinables profesorId/grupoId/anioId/materiaId. */
    @GetMapping
    public List<AsignacionRespuesta> listar(
            @RequestParam(name = "profesorId", required = false) Long profesorId,
            @RequestParam(name = "grupoId", required = false) Long grupoId,
            @RequestParam(name = "anioId", required = false) Long anioId,
            @RequestParam(name = "materiaId", required = false) Long materiaId) {
        return servicio.listar(profesorId, grupoId, anioId, materiaId);
    }

    /** Consulta una asignacion por id (cualquier rol autenticado). */
    @GetMapping("/{id}")
    public AsignacionRespuesta buscarPorId(@PathVariable Long id) {
        return servicio.buscarPorId(id);
    }

    /** Finaliza una asignacion ACTIVA (solo ADMINISTRADOR). */
    @PostMapping("/{id}/finalizar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public AsignacionRespuesta finalizar(@PathVariable Long id,
            Authentication sesion, HttpServletRequest request) {
        return servicio.finalizar(id, idUsuario(sesion), request.getRemoteAddr());
    }

    /** Reemplaza al profesor de una asignacion ACTIVA (solo ADMINISTRADOR). */
    @PostMapping("/{id}/reemplazar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public AsignacionRespuesta reemplazar(@PathVariable Long id,
            @RequestParam(name = "profesorId") Long profesorId,
            Authentication sesion, HttpServletRequest request) {
        return servicio.reemplazar(id, profesorId, idUsuario(sesion), request.getRemoteAddr());
    }

    /** El filtro de seguridad entrega el Usuario recargado de la base. */
    private Long idUsuario(Authentication sesion) {
        return ((Usuario) sesion.getPrincipal()).getId();
    }
}
