package com.semillero.evaluacion.calificaciones;

import com.semillero.evaluacion.calificaciones.dto.ActualizarCalificacionPeticion;
import com.semillero.evaluacion.calificaciones.dto.CalificacionRespuesta;
import com.semillero.evaluacion.calificaciones.dto.CrearCalificacionPeticion;
import com.semillero.evaluacion.comun.PaginaRespuesta;
import com.semillero.evaluacion.usuarios.Usuario;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.PageRequest;
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

    /**
     * Consulta PAGINADA de calificaciones; filtros combinables
     * estudianteId/asignacionId/materiaId/grupoId/anioId/periodoId.
     *
     * <p>Paginacion: {@code page} (indice 0-based, por defecto 0) y
     * {@code size} (registros por pagina, por defecto 20, maximo 100).</p>
     */
    @GetMapping
    public PaginaRespuesta<CalificacionRespuesta> listar(
            @Parameter(description = "Indice de pagina, 0-based")
            @RequestParam(name = "page", defaultValue = "0") @Min(0) Integer page,
            @Parameter(description = "Registros por pagina (1-100)")
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) Integer size,
            @Parameter(description = "Filtrar por estudiante")
            @RequestParam(name = "estudianteId", required = false) Long estudianteId,
            @Parameter(description = "Filtrar por asignacion")
            @RequestParam(name = "asignacionId", required = false) Long asignacionId,
            @Parameter(description = "Filtrar por materia")
            @RequestParam(name = "materiaId", required = false) Long materiaId,
            @Parameter(description = "Filtrar por grupo")
            @RequestParam(name = "grupoId", required = false) Long grupoId,
            @Parameter(description = "Filtrar por anio lectivo")
            @RequestParam(name = "anioId", required = false) Long anioId,
            @Parameter(description = "Filtrar por periodo")
            @RequestParam(name = "periodoId", required = false) Long periodoId) {
        return servicio.listar(estudianteId, asignacionId, materiaId, grupoId, anioId, periodoId,
                PageRequest.of(page, size));
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
