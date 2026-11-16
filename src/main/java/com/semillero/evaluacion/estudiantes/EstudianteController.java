package com.semillero.evaluacion.estudiantes;

import com.semillero.evaluacion.comun.PaginaRespuesta;
import com.semillero.evaluacion.estudiantes.dto.ActualizarEstudiantePeticion;
import com.semillero.evaluacion.estudiantes.dto.CrearEstudiantePeticion;
import com.semillero.evaluacion.estudiantes.dto.EstudianteRespuesta;
import com.semillero.evaluacion.matriculas.MatriculaServicio;
import com.semillero.evaluacion.matriculas.dto.MatriculaRespuesta;
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
 * Endpoints de estudiantes (/api/v1/estudiantes).
 *
 * <p>Permisos: la consulta es para cualquier rol autenticado; el registro,
 * la modificacion, la activacion y la desactivacion son exclusivos del
 * ADMINISTRADOR.</p>
 */
@RestController
@RequestMapping("/api/v1/estudiantes")
public class EstudianteController {

    private final EstudianteServicio servicio;
    private final MatriculaServicio matriculas;

    public EstudianteController(EstudianteServicio servicio, MatriculaServicio matriculas) {
        this.servicio = servicio;
        this.matriculas = matriculas;
    }

    /** Registra un estudiante con su cuenta (solo ADMINISTRADOR). */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EstudianteRespuesta> crear(
            @Valid @RequestBody CrearEstudiantePeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        EstudianteRespuesta respuesta = servicio.crear(peticion,
                idUsuario(sesion), request.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/v1/estudiantes/" + respuesta.id()))
                .body(respuesta);
    }

    /**
     * Consulta PAGINADA de estudiantes; con ?estado= filtra por ACTIVO o
     * INACTIVO.
     *
     * <p>Paginacion: {@code page} (indice 0-based, por defecto 0) y
     * {@code size} (registros por pagina, por defecto 20, maximo 100).</p>
     */
    @GetMapping
    public PaginaRespuesta<EstudianteRespuesta> listar(
            @Parameter(description = "Indice de pagina, 0-based")
            @RequestParam(name = "page", defaultValue = "0") @Min(0) Integer page,
            @Parameter(description = "Registros por pagina (1-100)")
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) Integer size,
            @Parameter(description = "Filtrar por estado del estudiante (ACTIVO/INACTIVO)")
            @RequestParam(name = "estado", required = false) String estado) {
        return servicio.listar(estado, PageRequest.of(page, size));
    }

    /** Consulta un estudiante por id (cualquier rol autenticado). */
    @GetMapping("/{id}")
    public EstudianteRespuesta buscarPorId(@PathVariable Long id) {
        return servicio.buscarPorId(id);
    }

    /** Consulta las matriculas de un estudiante (cualquier rol autenticado). */
    @GetMapping("/{id}/matriculas")
    public List<MatriculaRespuesta> matriculasDelEstudiante(@PathVariable Long id) {
        return matriculas.matriculasDe(id);
    }

    /** Actualiza los datos personales (solo ADMINISTRADOR). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public EstudianteRespuesta actualizar(@PathVariable Long id,
            @Valid @RequestBody ActualizarEstudiantePeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        return servicio.actualizar(id, peticion, idUsuario(sesion), request.getRemoteAddr());
    }

    /** Desactiva al estudiante y su cuenta (solo ADMINISTRADOR). */
    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public EstudianteRespuesta desactivar(@PathVariable Long id,
            Authentication sesion, HttpServletRequest request) {
        return servicio.desactivar(id, idUsuario(sesion), request.getRemoteAddr());
    }

    /** Reactiva al estudiante y su cuenta (solo ADMINISTRADOR). */
    @PostMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public EstudianteRespuesta activar(@PathVariable Long id,
            Authentication sesion, HttpServletRequest request) {
        return servicio.activar(id, idUsuario(sesion), request.getRemoteAddr());
    }

    /** El filtro de seguridad entrega el Usuario recargado de la base. */
    private Long idUsuario(Authentication sesion) {
        return ((Usuario) sesion.getPrincipal()).getId();
    }
}
