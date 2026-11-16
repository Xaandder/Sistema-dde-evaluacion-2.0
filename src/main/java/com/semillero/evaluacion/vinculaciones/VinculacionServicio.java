package com.semillero.evaluacion.vinculaciones;

import com.semillero.evaluacion.acudientes.Acudiente;
import com.semillero.evaluacion.acudientes.AcudienteRepositorio;
import com.semillero.evaluacion.acudientes.EstadoAcudiente;
import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.estudiantes.EstadoEstudiante;
import com.semillero.evaluacion.estudiantes.Estudiante;
import com.semillero.evaluacion.estudiantes.EstudianteRepositorio;
import com.semillero.evaluacion.exception.ConflictoEstadoException;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.vinculaciones.dto.CrearVinculacionPeticion;
import com.semillero.evaluacion.vinculaciones.dto.EstudianteVinculadoRespuesta;
import com.semillero.evaluacion.vinculaciones.dto.VinculacionRespuesta;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de negocio de las vinculaciones estudiante-acudiente (tabla
 * junction ESTUDIANTE_ACUDIENTE).
 *
 * <p>Reglas vigentes (coherentes con el DDL):</p>
 * <ul>
 *   <li>La fila ES la vinculacion autorizada: ambos participantes deben
 *       estar ACTIVOS (acudiente y estudiante).</li>
 *   <li>PK compuesta: una sola vinculacion por (estudiante, acudiente);
 *       duplicada es 409 CONFLICT.</li>
 *   <li>Un ACUDIENTE solo consulta los estudiantes con vinculacion
 *       autorizada SUYA; pedir datos de otros acudientes o estudiantes
 *       responde 403.</li>
 *   <li>Desvincular elimina la fila (la tabla junction lo permite: no tiene
 *       trigger de no borrado) y queda auditada.</li>
 *   <li>Toda operacion de escritura se audita (tabla append-only AUDITORIA).</li>
 * </ul>
 */
@Service
public class VinculacionServicio {

    private static final String ENTIDAD = "ESTUDIANTE_ACUDIENTE";

    private final VinculacionRepositorio repositorio;
    private final AcudienteRepositorio acudientes;
    private final EstudianteRepositorio estudiantes;
    private final AuditoriaServicio auditoria;

    public VinculacionServicio(VinculacionRepositorio repositorio,
            AcudienteRepositorio acudientes, EstudianteRepositorio estudiantes,
            AuditoriaServicio auditoria) {
        this.repositorio = repositorio;
        this.acudientes = acudientes;
        this.estudiantes = estudiantes;
        this.auditoria = auditoria;
    }

    /** Crea una vinculacion directa (solo ADMINISTRADOR). */
    @Transactional
    public VinculacionRespuesta vincular(CrearVinculacionPeticion peticion,
            Long usuarioId, String direccionIp) {
        Acudiente acudiente = buscarAcudiente(peticion.acudienteId());
        if (acudiente.getEstado() != EstadoAcudiente.ACTIVO) {
            throw new ConflictoEstadoException(
                    "El acudiente " + acudiente.getDocumento() + " esta INACTIVO: "
                            + "no admite vinculaciones");
        }

        Estudiante estudiante = buscarEstudiante(peticion.estudianteId());
        if (estudiante.getEstado() != EstadoEstudiante.ACTIVO) {
            throw new ConflictoEstadoException(
                    "El estudiante " + estudiante.getDocumento() + " esta INACTIVO: "
                            + "no admite vinculaciones");
        }

        if (repositorio.existsByEstudianteIdAndAcudienteId(
                estudiante.getId(), acudiente.getId())) {
            throw new ConflictoEstadoException(
                    "El estudiante " + estudiante.getDocumento() + " ya esta vinculado "
                            + "al acudiente " + acudiente.getDocumento());
        }

        EstudianteAcudiente vinculacion = new EstudianteAcudiente();
        vinculacion.setEstudianteId(estudiante.getId());
        vinculacion.setAcudienteId(acudiente.getId());
        EstudianteAcudiente guardada = repositorio.save(vinculacion);

        auditar("CREAR", guardada, usuarioId, Map.of(
                "estudiante", estudiante.getDocumento(),
                "acudiente", acudiente.getDocumento()), direccionIp);
        return aRespuesta(guardada);
    }

    /**
     * Consulta de vinculaciones (solo ADMINISTRADOR): filtros combinables
     * acudienteId y estudianteId.
     */
    @Transactional(readOnly = true)
    public List<VinculacionRespuesta> listar(Long acudienteId, Long estudianteId) {
        List<EstudianteAcudiente> filas;
        if (acudienteId != null) {
            filas = repositorio.findByAcudienteIdOrderByFechaVinculacionAsc(acudienteId);
        } else if (estudianteId != null) {
            filas = repositorio.findByEstudianteIdOrderByFechaVinculacionAsc(estudianteId);
        } else {
            filas = StreamSupport.stream(repositorio.findAll().spliterator(), false).toList();
        }
        return filas.stream().map(VinculacionServicio::aRespuesta).toList();
    }

    /**
     * Estudiantes vinculados de un acudiente (rol ACUDIENTE): exige que el
     * perfil consultado sea el de la sesion (403 si no). Datos basicos del
     * estudiante, resueltos con una sola consulta batch.
     */
    @Transactional(readOnly = true)
    public List<EstudianteVinculadoRespuesta> estudiantesDeMiAcudiente(
            Long acudienteId, Long usuarioIdSesion) {
        Acudiente acudiente = buscarAcudiente(acudienteId);
        if (!acudiente.getUsuarioId().equals(usuarioIdSesion)) {
            throw new AccessDeniedException(
                    "Un acudiente solo puede consultar sus propias vinculaciones");
        }

        List<EstudianteAcudiente> filas =
                repositorio.findByAcudienteIdOrderByFechaVinculacionAsc(acudienteId);
        List<Long> ids = filas.stream().map(EstudianteAcudiente::getEstudianteId).toList();
        Map<Long, Estudiante> datos = ids.isEmpty() ? Map.of() : StreamSupport
                .stream(estudiantes.findAllById(ids).spliterator(), false)
                .collect(Collectors.toMap(Estudiante::getId, Function.identity()));

        return filas.stream().map(v -> {
            Estudiante estudiante = datos.get(v.getEstudianteId());
            if (estudiante == null) {
                throw new RecursoNoEncontradoException(
                        "El estudiante vinculado " + v.getEstudianteId() + " no existe");
            }
            return new EstudianteVinculadoRespuesta(
                    estudiante.getId(),
                    estudiante.getDocumento(),
                    estudiante.getNombres(),
                    estudiante.getApellidos(),
                    estudiante.getEstado().name(),
                    v.getFechaVinculacion());
        }).toList();
    }

    /** Elimina una vinculacion (solo ADMINISTRADOR; la junction lo permite). */
    @Transactional
    public void desvincular(Long estudianteId, Long acudienteId, Long usuarioId,
            String direccionIp) {
        EstudianteAcudiente vinculacion = repositorio
                .findByEstudianteIdAndAcudienteId(estudianteId, acudienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una vinculacion del estudiante " + estudianteId
                                + " con el acudiente " + acudienteId));

        repositorio.delete(vinculacion);
        auditar("DESACTIVAR", vinculacion, usuarioId, Map.of(
                "estudiante", estudianteId, "acudiente", acudienteId), direccionIp);
    }

    private Acudiente buscarAcudiente(Long id) {
        return acudientes.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un acudiente con id " + id));
    }

    private Estudiante buscarEstudiante(Long id) {
        return estudiantes.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un estudiante con id " + id));
    }

    private void auditar(String accion, EstudianteAcudiente vinculacion, Long usuarioId,
            Map<String, Object> detalle, String direccionIp) {
        try {
            auditoria.registrar(accion, ENTIDAD,
                    vinculacion.getEstudianteId() + "-" + vinculacion.getAcudienteId(),
                    usuarioId, detalle, direccionIp);
        } catch (Exception ignorada) {
            // fail-open: el registro de auditoria nunca bloquea la operacion
        }
    }

    static VinculacionRespuesta aRespuesta(EstudianteAcudiente v) {
        return new VinculacionRespuesta(
                v.getEstudianteId(),
                v.getAcudienteId(),
                v.getFechaVinculacion());
    }
}
