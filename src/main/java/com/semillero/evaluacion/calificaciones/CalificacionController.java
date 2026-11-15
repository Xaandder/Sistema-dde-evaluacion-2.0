package com.semillero.evaluacion.calificaciones;

import com.semillero.evaluacion.calificaciones.dto.ActualizarCalificacionPeticion;
import com.semillero.evaluacion.calificaciones.dto.CalificacionRespuesta;
import com.semillero.evaluacion.calificaciones.dto.CrearCalificacionPeticion;
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
 * Endpoints de calificaciones (/api/v1/calificaciones).
 *
 * <p>Permisos: la escritura (registrar y editar notas) es exclusiva del rol
 * PROFESOR, y ademas el servicio exige que el docente autenticado sea el
 * titular de la ASIGNACION_ID (un no-docente o un co-docente de otra
 * asignacion recibe 404/403). Las consultas y el listado de espacios
 * pendientes son para cualquier rol autenticado.</p>
 */
@RestController
@RequestMapping("/api/v1/calificaciones")
public class CalificacionController {

    private final CalificacionServicio servicio;

    public CalificacionController(CalificacionServicio servicio) {
        this.servicio = servicio;
    }

    /** Registra una nota en un espacio de evaluacion libre (solo PROFESOR titular). */
    @PostMapping
    @PreAuthorize("hasRole('PROFESOR')")
    public ResponseEntity<CalificacionRespuesta> crear(
            @Valid @RequestBody CrearCalificacionPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        CalificacionRespuesta respuesta = servicio.crear(peticion,
                idUsuario(sesion), request.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/v1/calificaciones/" + respuesta.id()))
                .body(respuesta);
    }

    /** Edita nota y comentario de una calificacion propia (solo PROFESOR titular). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PROFESOR')")
    public CalificacionRespuesta editar(@PathVariable Long id,
            @Valid @RequestBody ActualizarCalificacionPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        return servicio.editar(id, peticion, idUsuario(sesion), request.getRemoteAddr());
    }

    /** Consulta calificaciones; filtros combinables estudianteId/asignacionId/materiaId/grupoId/anioId/periodoId. */
    @GetMapping
    public List<CalificacionRespuesta> listar(
            @RequestParam(name = "estudianteId", required = false) Long estudianteId,
            @RequestParam(name = "asignacionId", required = false) Long asignacionId,
            @RequestParam(name = "materiaId", required = false) Long materiaId,
            @RequestParam(name = "grupoId", required = false) Long grupoId,
            @RequestParam(name = "anioId", required = false) Long anioId,
            @RequestParam(name = "periodoId", required = false) Long periodoId) {
        return servicio.listar(estudianteId, asignacionId, materiaId, grupoId, anioId, periodoId);
    }

    /** Consulta una calificacion por id (cualquier rol autenticado). */
    @GetMapping("/{id}")
    public CalificacionRespuesta buscarPorId(@PathVariable Long id) {
        return servicio.buscarPorId(id);
    }

    /**
     * Espacios de evaluacion libres (1..10) de un estudiante en una
     * asignacion (cualquier rol autenticado).
     */
    @GetMapping("/pendientes")
    public List<Integer> pendientes(
            @RequestParam(name = "estudianteId") Long estudianteId,
            @RequestParam(name = "asignacionId") Long asignacionId) {
        return servicio.pendientes(estudianteId, asignacionId);
    }

    /** El filtro de seguridad entrega el Usuario recargado de la base. */
    private Long idUsuario(Authentication sesion) {
        return ((Usuario) sesion.getPrincipal()).getId();
    }
}
