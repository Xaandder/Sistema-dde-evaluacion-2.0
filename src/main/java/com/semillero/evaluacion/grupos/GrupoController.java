package com.semillero.evaluacion.grupos;

import com.semillero.evaluacion.grupos.dto.ActualizarGrupoPeticion;
import com.semillero.evaluacion.grupos.dto.CrearGrupoPeticion;
import com.semillero.evaluacion.grupos.dto.GrupoRespuesta;
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
 * Endpoints de grupos academicos (/api/v1/grupos).
 *
 * <p>Permisos: la consulta es para cualquier rol autenticado; el registro,
 * la modificacion, la activacion y la desactivacion son exclusivos del
 * ADMINISTRADOR.</p>
 */
@RestController
@RequestMapping("/api/v1/grupos")
public class GrupoController {

    private final GrupoServicio servicio;

    public GrupoController(GrupoServicio servicio) {
        this.servicio = servicio;
    }

    /** Crea un grupo en un anio lectivo (solo ADMINISTRADOR). */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<GrupoRespuesta> crear(@Valid @RequestBody CrearGrupoPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        GrupoRespuesta respuesta = servicio.crear(peticion,
                idUsuario(sesion), request.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/v1/grupos/" + respuesta.id()))
                .body(respuesta);
    }

    /** Consulta grupos; con ?anioId= filtra los de un anio (cualquier rol). */
    @GetMapping
    public List<GrupoRespuesta> listar(
            @RequestParam(name = "anioId", required = false) Long anioId) {
        return servicio.listar(anioId);
    }

    /** Consulta un grupo por id (cualquier rol autenticado). */
    @GetMapping("/{id}")
    public GrupoRespuesta buscarPorId(@PathVariable Long id) {
        return servicio.buscarPorId(id);
    }

    /** Modifica nombre y descripcion (solo ADMINISTRADOR). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public GrupoRespuesta actualizar(@PathVariable Long id,
            @Valid @RequestBody ActualizarGrupoPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        return servicio.actualizar(id, peticion, idUsuario(sesion), request.getRemoteAddr());
    }

    /** Desactiva un grupo (borrado logico, solo ADMINISTRADOR). */
    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public GrupoRespuesta desactivar(@PathVariable Long id,
            Authentication sesion, HttpServletRequest request) {
        return servicio.desactivar(id, idUsuario(sesion), request.getRemoteAddr());
    }

    /** Reactiva un grupo (solo ADMINISTRADOR). */
    @PostMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public GrupoRespuesta activar(@PathVariable Long id,
            Authentication sesion, HttpServletRequest request) {
        return servicio.activar(id, idUsuario(sesion), request.getRemoteAddr());
    }

    /** El filtro de seguridad entrega el Usuario recargado de la base. */
    private Long idUsuario(Authentication sesion) {
        return ((Usuario) sesion.getPrincipal()).getId();
    }
}
