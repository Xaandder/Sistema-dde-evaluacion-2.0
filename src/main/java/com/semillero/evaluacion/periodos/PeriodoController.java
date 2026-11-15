package com.semillero.evaluacion.periodos;

import com.semillero.evaluacion.periodos.dto.ActualizarPeriodoPeticion;
import com.semillero.evaluacion.periodos.dto.CrearPeriodoPeticion;
import com.semillero.evaluacion.periodos.dto.PeriodoRespuesta;
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
 * Endpoints de periodos academicos (/api/v1/periodos).
 *
 * <p>Permisos: la consulta es para cualquier rol autenticado; el registro,
 * la modificacion, la apertura y el cierre son exclusivos del ADMINISTRADOR.</p>
 */
@RestController
@RequestMapping("/api/v1/periodos")
public class PeriodoController {

    private final PeriodoServicio servicio;

    public PeriodoController(PeriodoServicio servicio) {
        this.servicio = servicio;
    }

    /** Registra un periodo asociado a un anio lectivo (solo ADMINISTRADOR). */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<PeriodoRespuesta> crear(@Valid @RequestBody CrearPeriodoPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        PeriodoRespuesta respuesta = servicio.crear(peticion,
                idUsuario(sesion), request.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/v1/periodos/" + respuesta.id()))
                .body(respuesta);
    }

    /** Consulta periodos; con ?anioId= filtra los de un anio (cualquier rol). */
    @GetMapping
    public List<PeriodoRespuesta> listar(
            @RequestParam(name = "anioId", required = false) Long anioId) {
        return servicio.listar(anioId);
    }

    /** Consulta un periodo por id (cualquier rol autenticado). */
    @GetMapping("/{id}")
    public PeriodoRespuesta buscarPorId(@PathVariable Long id) {
        return servicio.buscarPorId(id);
    }

    /** Modifica numero, nombre y fechas de un periodo no cerrado (solo ADMINISTRADOR). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public PeriodoRespuesta actualizar(@PathVariable Long id,
            @Valid @RequestBody ActualizarPeriodoPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        return servicio.actualizar(id, peticion, idUsuario(sesion), request.getRemoteAddr());
    }

    /** Abre un periodo PENDIENTE (solo ADMINISTRADOR). */
    @PostMapping("/{id}/abrir")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public PeriodoRespuesta abrir(@PathVariable Long id,
            Authentication sesion, HttpServletRequest request) {
        return servicio.abrir(id, idUsuario(sesion), request.getRemoteAddr());
    }

    /** Cierra un periodo ABIERTO (solo ADMINISTRADOR). */
    @PostMapping("/{id}/cerrar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public PeriodoRespuesta cerrar(@PathVariable Long id,
            Authentication sesion, HttpServletRequest request) {
        return servicio.cerrar(id, idUsuario(sesion), request.getRemoteAddr());
    }

    /** El filtro de seguridad entrega el Usuario recargado de la base. */
    private Long idUsuario(Authentication sesion) {
        return ((Usuario) sesion.getPrincipal()).getId();
    }
}
