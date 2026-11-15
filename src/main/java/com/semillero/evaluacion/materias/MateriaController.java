package com.semillero.evaluacion.materias;

import com.semillero.evaluacion.materias.dto.ActualizarMateriaPeticion;
import com.semillero.evaluacion.materias.dto.CrearMateriaPeticion;
import com.semillero.evaluacion.materias.dto.MateriaRespuesta;
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
 * Endpoints del catalogo de materias (/api/v1/materias).
 *
 * <p>Permisos: la consulta es para cualquier rol autenticado; el registro,
 * la modificacion, la activacion y la desactivacion son exclusivos del
 * ADMINISTRADOR.</p>
 */
@RestController
@RequestMapping("/api/v1/materias")
public class MateriaController {

    private final MateriaServicio servicio;

    public MateriaController(MateriaServicio servicio) {
        this.servicio = servicio;
    }

    /** Registra una materia (solo ADMINISTRADOR). */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<MateriaRespuesta> crear(@Valid @RequestBody CrearMateriaPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        MateriaRespuesta respuesta = servicio.crear(peticion,
                idUsuario(sesion), request.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/v1/materias/" + respuesta.id()))
                .body(respuesta);
    }

    /** Consulta el catalogo; con ?estado= filtra por ACTIVO o INACTIVO. */
    @GetMapping
    public List<MateriaRespuesta> listar(
            @RequestParam(name = "estado", required = false) String estado) {
        return servicio.listar(estado);
    }

    /** Consulta una materia por id (cualquier rol autenticado). */
    @GetMapping("/{id}")
    public MateriaRespuesta buscarPorId(@PathVariable Long id) {
        return servicio.buscarPorId(id);
    }

    /** Modifica codigo, nombre y descripcion (solo ADMINISTRADOR). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public MateriaRespuesta actualizar(@PathVariable Long id,
            @Valid @RequestBody ActualizarMateriaPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        return servicio.actualizar(id, peticion, idUsuario(sesion), request.getRemoteAddr());
    }

    /** Desactiva una materia (borrado logico, solo ADMINISTRADOR). */
    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public MateriaRespuesta desactivar(@PathVariable Long id,
            Authentication sesion, HttpServletRequest request) {
        return servicio.desactivar(id, idUsuario(sesion), request.getRemoteAddr());
    }

    /** Reactiva una materia (solo ADMINISTRADOR). */
    @PostMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public MateriaRespuesta activar(@PathVariable Long id,
            Authentication sesion, HttpServletRequest request) {
        return servicio.activar(id, idUsuario(sesion), request.getRemoteAddr());
    }

    /** El filtro de seguridad entrega el Usuario recargado de la base. */
    private Long idUsuario(Authentication sesion) {
        return ((Usuario) sesion.getPrincipal()).getId();
    }
}
