package com.semillero.evaluacion.matriculas;

import com.semillero.evaluacion.comun.PaginaRespuesta;
import com.semillero.evaluacion.matriculas.dto.CrearMatriculaPeticion;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de matriculas (/api/v1/matriculas).
 *
 * <p>Permisos: la consulta es para cualquier rol autenticado; la matricula
 * y el retiro son exclusivos del ADMINISTRADOR.</p>
 */
@RestController
@RequestMapping("/api/v1/matriculas")
public class MatriculaController {

    private final MatriculaServicio servicio;

    public MatriculaController(MatriculaServicio servicio) {
        this.servicio = servicio;
    }

    /** Matricula a un estudiante en un grupo (solo ADMINISTRADOR). */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<MatriculaRespuesta> crear(
            @Valid @RequestBody CrearMatriculaPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        MatriculaRespuesta respuesta = servicio.crear(peticion,
                idUsuario(sesion), request.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/v1/matriculas/" + respuesta.id()))
                .body(respuesta);
    }

    /**
     * Consulta PAGINADA de matriculas; filtros combinables ?estudianteId= y
     * ?anioId=.
     *
     * <p>Paginacion: {@code page} (indice 0-based, por defecto 0) y
     * {@code size} (registros por pagina, por defecto 20, maximo 100).</p>
     */
    @GetMapping
    public PaginaRespuesta<MatriculaRespuesta> listar(
            @Parameter(description = "Indice de pagina, 0-based")
            @RequestParam(name = "page", defaultValue = "0") @Min(0) Integer page,
            @Parameter(description = "Registros por pagina (1-100)")
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) Integer size,
            @Parameter(description = "Filtrar por estudiante")
            @RequestParam(name = "estudianteId", required = false) Long estudianteId,
            @Parameter(description = "Filtrar por anio lectivo")
            @RequestParam(name = "anioId", required = false) Long anioId) {
        return servicio.listar(estudianteId, anioId, PageRequest.of(page, size));
    }

    /** Consulta una matricula por id (cualquier rol autenticado). */
    @GetMapping("/{id}")
    public MatriculaRespuesta buscarPorId(@PathVariable Long id) {
        return servicio.buscarPorId(id);
    }

    /** Retira una matricula ACTIVA (solo ADMINISTRADOR). */
    @PostMapping("/{id}/retirar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public MatriculaRespuesta retirar(@PathVariable Long id,
            Authentication sesion, HttpServletRequest request) {
        return servicio.retirar(id, idUsuario(sesion), request.getRemoteAddr());
    }

    /** El filtro de seguridad entrega el Usuario recargado de la base. */
    private Long idUsuario(Authentication sesion) {
        return ((Usuario) sesion.getPrincipal()).getId();
    }
}
