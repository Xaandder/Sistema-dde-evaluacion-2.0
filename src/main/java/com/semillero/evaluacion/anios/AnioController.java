package com.semillero.evaluacion.anios;

import com.semillero.evaluacion.anios.dto.ActualizarAnioPeticion;
import com.semillero.evaluacion.anios.dto.AnioRespuesta;
import com.semillero.evaluacion.anios.dto.CrearAnioPeticion;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de anios lectivos (/api/v1/anios).
 *
 * <p>Permisos: la consulta es para cualquier rol autenticado; el registro,
 * la modificacion, la activacion y la finalizacion son exclusivos del
 * ADMINISTRADOR.</p>
 */
@RestController
@RequestMapping("/api/v1/anios")
public class AnioController {

    private final AnioServicio servicio;

    public AnioController(AnioServicio servicio) {
        this.servicio = servicio;
    }

    /** Registra un anio lectivo (solo ADMINISTRADOR). */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<AnioRespuesta> crear(@Valid @RequestBody CrearAnioPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        AnioRespuesta respuesta = servicio.crear(peticion,
                idUsuario(sesion), request.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/v1/anios/" + respuesta.id()))
                .body(respuesta);
    }

    /** Consulta todos los anios lectivos (cualquier rol autenticado). */
    @GetMapping
    public List<AnioRespuesta> listar() {
        return servicio.listar();
    }

    /** Consulta un anio lectivo por id (cualquier rol autenticado). */
    @GetMapping("/{id}")
    public AnioRespuesta buscarPorId(@PathVariable Long id) {
        return servicio.buscarPorId(id);
    }

    /** Modifica la informacion de un anio ACTIVO (solo ADMINISTRADOR). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public AnioRespuesta actualizar(@PathVariable Long id,
            @Valid @RequestBody ActualizarAnioPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        return servicio.actualizar(id, peticion, idUsuario(sesion), request.getRemoteAddr());
    }

    /** Activa un anio lectivo (solo ADMINISTRADOR). */
    @PostMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public AnioRespuesta activar(@PathVariable Long id,
            Authentication sesion, HttpServletRequest request) {
        return servicio.activar(id, idUsuario(sesion), request.getRemoteAddr());
    }

    /** Finaliza (cierra) un anio lectivo (solo ADMINISTRADOR). */
    @PostMapping("/{id}/cerrar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public AnioRespuesta cerrar(@PathVariable Long id,
            Authentication sesion, HttpServletRequest request) {
        return servicio.cerrar(id, idUsuario(sesion), request.getRemoteAddr());
    }

    /** El filtro de seguridad entrega el Usuario recargado de la base. */
    private Long idUsuario(Authentication sesion) {
        return ((Usuario) sesion.getPrincipal()).getId();
    }
}
