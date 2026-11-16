package com.semillero.evaluacion.solicitudes;

import com.semillero.evaluacion.solicitudes.dto.CrearSolicitudPeticion;
import com.semillero.evaluacion.solicitudes.dto.ResolucionSolicitudPeticion;
import com.semillero.evaluacion.solicitudes.dto.SolicitudRespuesta;
import com.semillero.evaluacion.usuarios.Usuario;
import io.swagger.v3.oas.annotations.Parameter;
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
 * Endpoints de solicitudes de vinculacion
 * (/api/v1/solicitudes-vinculacion).
 *
 * <p>Permisos: el ACUDIENTE crea solicitudes para si mismo y consulta solo
 * las SUYAS; el ADMINISTRADOR las resuelve (aprobar/rechazar, con trazas de
 * REVISADO_POR) y consulta todas. Ningun otro rol participa.</p>
 */
@RestController
@RequestMapping("/api/v1/solicitudes-vinculacion")
public class SolicitudController {

    private final SolicitudServicio servicio;

    public SolicitudController(SolicitudServicio servicio) {
        this.servicio = servicio;
    }

    /** Crea una solicitud PENDIENTE del acudiente autenticado (solo ACUDIENTE). */
    @PostMapping
    @PreAuthorize("hasRole('ACUDIENTE')")
    public ResponseEntity<SolicitudRespuesta> crear(
            @Valid @RequestBody CrearSolicitudPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        SolicitudRespuesta respuesta = servicio.crear(peticion,
                idUsuario(sesion), request.getRemoteAddr());
        return ResponseEntity.created(
                URI.create("/api/v1/solicitudes-vinculacion/" + respuesta.id()))
                .body(respuesta);
    }

    /**
     * Consulta solicitudes: ADMIN todas; ACUDIENTE solo las suyas. Filtros
     * combinables estado/acudienteId/estudianteId.
     */
    @GetMapping
    public List<SolicitudRespuesta> listar(
            @Parameter(description = "Filtrar por estado (PENDIENTE/APROBADA/RECHAZADA)")
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "acudienteId", required = false) Long acudienteId,
            @RequestParam(name = "estudianteId", required = false) Long estudianteId,
            Authentication sesion) {
        return servicio.listar(estado, acudienteId, estudianteId,
                idUsuario(sesion), esAdmin(sesion));
    }

    /** Aprueba una solicitud PENDIENTE y crea la vinculacion (solo ADMINISTRADOR). */
    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public SolicitudRespuesta aprobar(@PathVariable Long id,
            @Valid @RequestBody(required = false) ResolucionSolicitudPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        String observacion = peticion != null ? peticion.observacion() : null;
        return servicio.aprobar(id, observacion, idUsuario(sesion), request.getRemoteAddr());
    }

    /** Rechaza una solicitud PENDIENTE (solo ADMINISTRADOR). */
    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public SolicitudRespuesta rechazar(@PathVariable Long id,
            @Valid @RequestBody(required = false) ResolucionSolicitudPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        String observacion = peticion != null ? peticion.observacion() : null;
        return servicio.rechazar(id, observacion, idUsuario(sesion), request.getRemoteAddr());
    }

    private Long idUsuario(Authentication sesion) {
        return ((Usuario) sesion.getPrincipal()).getId();
    }

    private boolean esAdmin(Authentication sesion) {
        return sesion.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));
    }
}
