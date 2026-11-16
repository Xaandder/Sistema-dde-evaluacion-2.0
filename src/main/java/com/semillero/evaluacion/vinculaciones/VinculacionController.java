package com.semillero.evaluacion.vinculaciones;

import com.semillero.evaluacion.usuarios.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.semillero.evaluacion.vinculaciones.dto.CrearVinculacionPeticion;
import com.semillero.evaluacion.vinculaciones.dto.EstudianteVinculadoRespuesta;
import com.semillero.evaluacion.vinculaciones.dto.VinculacionRespuesta;

/**
 * Endpoints de vinculaciones estudiante-acudiente
 * (/api/v1/vinculaciones).
 *
 * <p>Permisos: crear y desvincular son del ADMINISTRADOR. El rol ACUDIENTE
 * solo puede consultar SUS vinculaciones ({@code /mis-estudiantes}: si
 * pidiera el perfil de otro acudiente responde 403) y nunca recibe datos de
 * estudiantes ajenos. El listado completo es del ADMINISTRADOR.</p>
 */
@RestController
@RequestMapping("/api/v1/vinculaciones")
public class VinculacionController {

    private final VinculacionServicio servicio;

    public VinculacionController(VinculacionServicio servicio) {
        this.servicio = servicio;
    }

    /** Crea una vinculacion directa (solo ADMINISTRADOR). */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<VinculacionRespuesta> vincular(
            @Valid @RequestBody CrearVinculacionPeticion peticion,
            Authentication sesion, HttpServletRequest request) {
        VinculacionRespuesta respuesta = servicio.vincular(peticion,
                idUsuario(sesion), request.getRemoteAddr());
        return ResponseEntity.ok(respuesta);
    }

    /** Consulta vinculaciones; filtros combinables acudienteId/estudianteId (solo ADMIN). */
    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<VinculacionRespuesta> listar(
            @RequestParam(name = "acudienteId", required = false) Long acudienteId,
            @RequestParam(name = "estudianteId", required = false) Long estudianteId) {
        return servicio.listar(acudienteId, estudianteId);
    }

    /**
     * Estudiantes vinculados del ACUDIENTE autenticado (unico acesso de
     * lectura del rol ACUDIENTE sobre estudiantes).
     */
    @GetMapping("/mis-estudiantes")
    @PreAuthorize("hasRole('ACUDIENTE')")
    public List<EstudianteVinculadoRespuesta> misEstudiantes(
            @RequestParam(name = "acudienteId") Long acudienteId,
            Authentication sesion) {
        return servicio.estudiantesDeMiAcudiente(acudienteId, idUsuario(sesion));
    }

    /** Elimina una vinculacion (solo ADMINISTRADOR). */
    @DeleteMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> desvincular(
            @RequestParam(name = "estudianteId") Long estudianteId,
            @RequestParam(name = "acudienteId") Long acudienteId,
            Authentication sesion, HttpServletRequest request) {
        servicio.desvincular(estudianteId, acudienteId, idUsuario(sesion),
                request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    private Long idUsuario(Authentication sesion) {
        return ((Usuario) sesion.getPrincipal()).getId();
    }
}
