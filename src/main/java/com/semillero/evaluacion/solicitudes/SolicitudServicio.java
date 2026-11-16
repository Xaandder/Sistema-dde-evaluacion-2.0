package com.semillero.evaluacion.solicitudes;

import com.semillero.evaluacion.acudientes.Acudiente;
import com.semillero.evaluacion.acudientes.AcudienteRepositorio;
import com.semillero.evaluacion.acudientes.EstadoAcudiente;
import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.estudiantes.EstadoEstudiante;
import com.semillero.evaluacion.estudiantes.Estudiante;
import com.semillero.evaluacion.estudiantes.EstudianteRepositorio;
import com.semillero.evaluacion.exception.ConflictoEstadoException;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.solicitudes.dto.CrearSolicitudPeticion;
import com.semillero.evaluacion.solicitudes.dto.SolicitudRespuesta;
import com.semillero.evaluacion.vinculaciones.EstudianteAcudiente;
import com.semillero.evaluacion.vinculaciones.VinculacionRepositorio;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de negocio de las solicitudes de vinculacion (tabla
 * SOLICITUDES_VINCULACION).
 *
 * <p>Reglas vigentes (coherentes con el DDL):</p>
 * <ul>
 *   <li>La crea el ACUDIENTE (el servicio resuelve su perfil por el
 *       USUARIO_ID de la sesion: un acudiente no puede solicitar para
 *       otro). La solicitud nace PENDIENTE.</li>
 *   <li>El acudiente debe estar ACTIVO y el estudiante existir y estar
 *       ACTIVO.</li>
 *   <li>No se acepta una solicitud si la vinculacion ya existe o si hay
 *       otra PENDIENTE del mismo par (409 CONFLICT); tras una RECHAZADA si
 *       se puede volver a solicitar (el DDL no lo prohibe).</li>
 *   <li>Solo el ADMINISTRADOR la resuelve: APROBADA crea la fila en
 *       ESTUDIANTE_ACUDIENTE con REVISADO_POR y FECHA_RESOLUCION;
 *       RECHAZADA solo deja la traza. Una solicitud resuelta no se
 *       re-resuelve.</li>
 *   <li>Sin borrado fisico de solicitudes.</li>
 *   <li>Toda operacion de escritura se audita (tabla append-only AUDITORIA).</li>
 * </ul>
 */
@Service
public class SolicitudServicio {

    private static final String ENTIDAD = "SOLICITUDES_VINCULACION";

    private final SolicitudRepositorio repositorio;
    private final AcudienteRepositorio acudientes;
    private final EstudianteRepositorio estudiantes;
    private final VinculacionRepositorio vinculaciones;
    private final AuditoriaServicio auditoria;

    public SolicitudServicio(SolicitudRepositorio repositorio,
            AcudienteRepositorio acudientes, EstudianteRepositorio estudiantes,
            VinculacionRepositorio vinculaciones, AuditoriaServicio auditoria) {
        this.repositorio = repositorio;
        this.acudientes = acudientes;
        this.estudiantes = estudiantes;
        this.vinculaciones = vinculaciones;
        this.auditoria = auditoria;
    }

    /** Crea una solicitud PENDIENTE del acudiente autenticado (rol ACUDIENTE). */
    @Transactional
    public SolicitudRespuesta crear(CrearSolicitudPeticion peticion,
            Long usuarioId, String direccionIp) {
        Acudiente acudiente = acudientes.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La cuenta autenticada no esta vinculada a un acudiente"));
        if (acudiente.getEstado() != EstadoAcudiente.ACTIVO) {
            throw new ConflictoEstadoException(
                    "El acudiente esta INACTIVO: no puede solicitar vinculaciones");
        }

        Estudiante estudiante = estudiantes.findById(peticion.estudianteId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un estudiante con id " + peticion.estudianteId()));
        if (estudiante.getEstado() != EstadoEstudiante.ACTIVO) {
            throw new ConflictoEstadoException(
                    "El estudiante " + estudiante.getDocumento() + " esta INACTIVO: "
                            + "no admite vinculaciones");
        }

        if (vinculaciones.existsByEstudianteIdAndAcudienteId(
                estudiante.getId(), acudiente.getId())) {
            throw new ConflictoEstadoException(
                    "El estudiante " + estudiante.getDocumento() + " ya esta vinculado "
                            + "a tu acudiente");
        }
        if (repositorio.existsByAcudienteIdAndEstudianteIdAndEstado(
                acudiente.getId(), estudiante.getId(), EstadoSolicitud.PENDIENTE)) {
            throw new ConflictoEstadoException(
                    "Ya existe una solicitud PENDIENTE para el estudiante "
                            + estudiante.getDocumento());
        }

        SolicitudVinculacion solicitud = new SolicitudVinculacion();
        solicitud.setAcudienteId(acudiente.getId());
        solicitud.setEstudianteId(estudiante.getId());
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        solicitud.setObservacion(peticion.observacion());
        SolicitudVinculacion guardada = repositorio.save(solicitud);

        auditar("CREAR", guardada, usuarioId, Map.of(
                "estudiante", estudiante.getDocumento()), direccionIp);
        return aRespuesta(guardada);
    }

    /**
     * Consulta de solicitudes: el ADMINISTRADOR ve todas (con filtros
     * combinables estado/acudienteId/estudianteId); el ACUDIENTE solo ve las
     * SUYAS (403 si pide otras).
     */
    @Transactional(readOnly = true)
    public List<SolicitudRespuesta> listar(String estado, Long acudienteId, Long estudianteId,
            Long usuarioIdSesion, boolean esAdmin) {
        Long filtroAcudiente = acudienteId;
        if (!esAdmin) {
            Acudiente propio = acudientes.findByUsuarioId(usuarioIdSesion)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "La cuenta autenticada no esta vinculada a un acudiente"));
            if (filtroAcudiente != null && !filtroAcudiente.equals(propio.getId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Un acudiente solo puede consultar sus propias solicitudes");
            }
            filtroAcudiente = propio.getId();
        }

        EstadoSolicitud estadoFiltro = estado != null ? estadoValido(estado) : null;
        List<SolicitudVinculacion> filas;
        if (filtroAcudiente != null && estadoFiltro != null) {
            filas = repositorio.findByAcudienteIdAndEstadoOrderByFechaCreacionAsc(
                    filtroAcudiente, estadoFiltro);
        } else if (estudianteId != null && estadoFiltro != null) {
            filas = repositorio.findByEstudianteIdAndEstadoOrderByFechaCreacionAsc(
                    estudianteId, estadoFiltro);
        } else if (filtroAcudiente != null) {
            filas = repositorio.findByAcudienteIdOrderByFechaCreacionAsc(filtroAcudiente);
        } else if (estudianteId != null) {
            filas = repositorio.findByEstudianteIdOrderByFechaCreacionAsc(estudianteId);
        } else if (estadoFiltro != null) {
            filas = repositorio.findByEstadoOrderByFechaCreacionAsc(estadoFiltro);
        } else {
            filas = repositorio.findAllByOrderByFechaCreacionAsc();
        }
        return filas.stream().map(SolicitudServicio::aRespuesta).toList();
    }

    /** Aprueba una solicitud PENDIENTE y crea la vinculacion (solo ADMINISTRADOR). */
    @Transactional
    public SolicitudRespuesta aprobar(Long id, String observacion,
            Long usuarioId, String direccionIp) {
        SolicitudVinculacion solicitud = pendiente(id);
        Estudiante estudiante = estudiantes.findById(solicitud.getEstudianteId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El estudiante de la solicitud no existe"));

        if (vinculaciones.existsByEstudianteIdAndAcudienteId(
                solicitud.getEstudianteId(), solicitud.getAcudienteId())) {
            throw new ConflictoEstadoException(
                    "La vinculacion del estudiante " + estudiante.getDocumento()
                            + " con este acudiente ya existe");
        }

        resolver(solicitud, EstadoSolicitud.APROBADA, observacion, usuarioId);

        EstudianteAcudiente vinculacion = new EstudianteAcudiente();
        vinculacion.setEstudianteId(solicitud.getEstudianteId());
        vinculacion.setAcudienteId(solicitud.getAcudienteId());
        vinculaciones.save(vinculacion);

        auditar("ACTUALIZAR", solicitud, usuarioId, Map.of(
                "resolucion", "APROBADA", "estudiante", estudiante.getDocumento()),
                direccionIp);
        return aRespuesta(solicitud);
    }

    /** Rechaza una solicitud PENDIENTE (solo ADMINISTRADOR). */
    @Transactional
    public SolicitudRespuesta rechazar(Long id, String observacion,
            Long usuarioId, String direccionIp) {
        SolicitudVinculacion solicitud = pendiente(id);
        resolver(solicitud, EstadoSolicitud.RECHAZADA, observacion, usuarioId);

        auditar("ACTUALIZAR", solicitud, usuarioId,
                Map.of("resolucion", "RECHAZADA"), direccionIp);
        return aRespuesta(solicitud);
    }

    private SolicitudVinculacion pendiente(Long id) {
        SolicitudVinculacion solicitud = repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una solicitud con id " + id));
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new ConflictoEstadoException(
                    "Solo se puede resolver una solicitud PENDIENTE (estado actual: "
                            + solicitud.getEstado() + ")");
        }
        return solicitud;
    }

    private void resolver(SolicitudVinculacion solicitud, EstadoSolicitud estado,
            String observacion, Long usuarioId) {
        solicitud.setEstado(estado);
        solicitud.setRevisadoPor(usuarioId);
        solicitud.setFechaResolucion(java.time.LocalDateTime.now());
        if (observacion != null) {
            solicitud.setObservacion(observacion);
        }
        repositorio.save(solicitud);
    }

    private EstadoSolicitud estadoValido(String estado) {
        try {
            return EstadoSolicitud.valueOf(estado);
        } catch (IllegalArgumentException ex) {
            throw new com.semillero.evaluacion.exception.ReglaNegocioException(
                    "El estado debe ser PENDIENTE, APROBADA o RECHAZADA (recibido: "
                            + estado + ")");
        }
    }

    private void auditar(String accion, SolicitudVinculacion solicitud, Long usuarioId,
            Map<String, Object> detalle, String direccionIp) {
        try {
            auditoria.registrar(accion, ENTIDAD, String.valueOf(solicitud.getId()),
                    usuarioId, detalle, direccionIp);
        } catch (Exception ignorada) {
            // fail-open: el registro de auditoria nunca bloquea la operacion
        }
    }

    static SolicitudRespuesta aRespuesta(SolicitudVinculacion s) {
        return new SolicitudRespuesta(
                s.getId(),
                s.getAcudienteId(),
                s.getEstudianteId(),
                s.getEstado().name(),
                s.getFechaCreacion(),
                s.getRevisadoPor(),
                s.getFechaResolucion(),
                s.getObservacion());
    }
}
