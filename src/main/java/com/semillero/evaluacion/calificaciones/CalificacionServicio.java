package com.semillero.evaluacion.calificaciones;

import com.semillero.evaluacion.asignaciones.Asignacion;
import com.semillero.evaluacion.asignaciones.AsignacionRepositorio;
import com.semillero.evaluacion.asignaciones.EstadoAsignacion;
import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.calificaciones.dto.ActualizarCalificacionPeticion;
import com.semillero.evaluacion.calificaciones.dto.CalificacionRespuesta;
import com.semillero.evaluacion.calificaciones.dto.CrearCalificacionPeticion;
import com.semillero.evaluacion.comun.PaginaRespuesta;
import com.semillero.evaluacion.exception.ConflictoEstadoException;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.exception.ReglaNegocioException;
import com.semillero.evaluacion.matriculas.EstadoMatricula;
import com.semillero.evaluacion.matriculas.Matricula;
import com.semillero.evaluacion.matriculas.MatriculaRepositorio;
import com.semillero.evaluacion.periodos.EstadoPeriodo;
import com.semillero.evaluacion.periodos.Periodo;
import com.semillero.evaluacion.periodos.PeriodoRepositorio;
import com.semillero.evaluacion.profesores.Profesor;
import com.semillero.evaluacion.profesores.ProfesorRepositorio;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de negocio de las calificaciones (tabla CALIFICACIONES).
 *
 * <p>El cliente solo envia matriculaId, asignacionId, numeroEvaluacion,
 * nota y comentario; el resto de las columnas se deriva: ESTUDIANTE_ID de la
 * matricula y MATERIA_ID/GRUPO_ID/ANIO_ID/PERIODO_ID de la asignacion. La
 * base lo ancla con las FKs compuestas FK_CAL_MATRICULA y
 * FK_CAL_ASIGNACION, y Java repite esas comprobaciones con mensajes
 * entendibles ANTES de chocar contra Oracle.</p>
 *
 * <p>Reglas vigentes:</p>
 * <ul>
 *   <li>Solo escribe el profesor dueño de la ASIGNACION_ID (el
 *       co-docente de otro grupo tiene su propia asignacion): un profesor
 *       que no es el titular recibe 403.</li>
 *   <li>La asignacion debe estar ACTIVA, el periodo ABIERTO y la matricula
 *       ACTIVA.</li>
 *   <li>La matricula debe corresponder al mismo grupo y anio de la
 *       asignacion (FK_CAL_MATRICULA).</li>
 *   <li>UNIQUE (ESTUDIANTE, MATERIA, PERIODO, NUMERO): los co-docentes
 *       comparten los 10 espacios de evaluacion; crear sobre un espacio
 *       ocupado es 400 (nunca se sobrescribe).</li>
 *   <li>numeroEvaluacion 1..10 y nota 0.0..5.0 con dos decimales; ambos
 *       fijos desde el alta (solo se editan nota y comentario).</li>
 *   <li>Sin DELETE: la nota se registra y se edita; la trazabilidad queda
 *       en REGISTRADO_POR/ACTUALIZADO_POR y en la auditoria append-only.</li>
 * </ul>
 */
@Service
public class CalificacionServicio {

    private static final String ENTIDAD = "CALIFICACIONES";

    private final CalificacionRepositorio repositorio;
    private final MatriculaRepositorio matriculas;
    private final AsignacionRepositorio asignaciones;
    private final PeriodoRepositorio periodos;
    private final ProfesorRepositorio profesores;
    private final AuditoriaServicio auditoria;

    public CalificacionServicio(CalificacionRepositorio repositorio,
            MatriculaRepositorio matriculas, AsignacionRepositorio asignaciones,
            PeriodoRepositorio periodos, ProfesorRepositorio profesores,
            AuditoriaServicio auditoria) {
        this.repositorio = repositorio;
        this.matriculas = matriculas;
        this.asignaciones = asignaciones;
        this.periodos = periodos;
        this.profesores = profesores;
        this.auditoria = auditoria;
    }

    /** Registra una nota en un espacio de evaluacion libre. */
    @Transactional
    public CalificacionRespuesta crear(CrearCalificacionPeticion peticion,
            Long usuarioId, String direccionIp) {
        Profesor profesor = profesorDeLaSesion(usuarioId);
        Asignacion asignacion = asignacionDelProfesor(peticion.asignacionId(), profesor);

        Periodo periodo = periodos.findById(asignacion.getPeriodoId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La asignacion referencia un periodo inexistente"));
        if (periodo.getEstado() != EstadoPeriodo.ABIERTO) {
            throw new ReglaNegocioException(
                    "El periodo no esta ABIERTO (estado actual: " + periodo.getEstado()
                            + "): no admite calificaciones");
        }

        Matricula matricula = matriculas.findById(peticion.matriculaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una matricula con id " + peticion.matriculaId()));
        if (matricula.getEstado() != EstadoMatricula.ACTIVA) {
            throw new ReglaNegocioException(
                    "La matricula no esta ACTIVA (estado actual: " + matricula.getEstado()
                            + "): no admite calificaciones");
        }

        // FK_CAL_MATRICULA: la matricula debe ser del grupo y anio de la asignacion.
        if (!matricula.getGrupoId().equals(asignacion.getGrupoId())
                || !matricula.getAnioId().equals(asignacion.getAnioId())) {
            throw new ReglaNegocioException(
                    "La matricula pertenece a otro grupo o anio lectivo: solo se "
                            + "califica a estudiantes matriculados en el grupo y anio "
                            + "de la asignacion");
        }

        // UNIQUE (ESTUDIANTE, MATERIA, PERIODO, NUMERO): espacio compartido
        // entre co-docentes; ocupado -> 400, nunca se sobrescribe.
        repositorio.findByEstudianteIdAndMateriaIdAndPeriodoIdAndNumeroEvaluacion(
                matricula.getEstudianteId(), asignacion.getMateriaId(),
                asignacion.getPeriodoId(), peticion.numeroEvaluacion())
                .ifPresent(ocupado -> {
                    throw new ConflictoEstadoException(
                            "La evaluacion " + peticion.numeroEvaluacion() + " del "
                                    + "estudiante en esta materia y periodo ya esta "
                                    + "registrada (calificacion id " + ocupado.getId() + ")");
                });

        Calificacion calificacion = new Calificacion();
        calificacion.setMatriculaId(matricula.getId());
        calificacion.setEstudianteId(matricula.getEstudianteId());
        calificacion.setAsignacionId(asignacion.getId());
        calificacion.setMateriaId(asignacion.getMateriaId());
        calificacion.setGrupoId(asignacion.getGrupoId());
        calificacion.setAnioId(asignacion.getAnioId());
        calificacion.setPeriodoId(asignacion.getPeriodoId());
        calificacion.setNumeroEvaluacion(peticion.numeroEvaluacion());
        calificacion.setNota(peticion.nota().setScale(2));
        calificacion.setComentario(peticion.comentario());
        calificacion.setRegistradoPor(usuarioId);
        Calificacion guardada = repositorio.save(calificacion);

        auditar("CREAR", guardada, usuarioId, direccionIp);
        return aRespuesta(guardada);
    }

    /**
     * Edita solo nota y comentario de una nota propia. La asignacion es
     * inmutable: se revalida con {@link CalificacionAsignacionValidator} que
     * la fila conservo sus cinco identificadores originales.
     */
    @Transactional
    public CalificacionRespuesta editar(Long id, ActualizarCalificacionPeticion peticion,
            Long usuarioId, String direccionIp) {
        Profesor profesor = profesorDeLaSesion(usuarioId);
        Calificacion calificacion = buscarEntidad(id);
        Asignacion asignacion = asignaciones.findById(calificacion.getAsignacionId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La calificacion referencia una asignacion inexistente"));
        if (!esTitular(asignacion, profesor)) {
            throw new AccessDeniedException(
                    "Solo el profesor que registro la asignacion puede editar esta "
                            + "calificacion");
        }

        CalificacionAsignacionValidator.validarSinCambios(
                new CalificacionAsignacionValidator.Asignacion(
                        asignacion.getId(), asignacion.getMateriaId(), asignacion.getGrupoId(),
                        asignacion.getAnioId(), asignacion.getPeriodoId()),
                new CalificacionAsignacionValidator.Asignacion(
                        calificacion.getAsignacionId(), calificacion.getMateriaId(),
                        calificacion.getGrupoId(), calificacion.getAnioId(),
                        calificacion.getPeriodoId()));

        Periodo periodo = periodos.findById(asignacion.getPeriodoId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La asignacion referencia un periodo inexistente"));
        if (periodo.getEstado() != EstadoPeriodo.ABIERTO) {
            throw new ReglaNegocioException(
                    "El periodo no esta ABIERTO (estado actual: " + periodo.getEstado()
                            + "): no admite ediciones de calificaciones");
        }
        Matricula matricula = matriculas.findById(calificacion.getMatriculaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La calificacion referencia una matricula inexistente"));
        if (matricula.getEstado() != EstadoMatricula.ACTIVA) {
            throw new ReglaNegocioException(
                    "La matricula no esta ACTIVA (estado actual: " + matricula.getEstado()
                            + "): no admite ediciones de calificaciones");
        }

        calificacion.setNota(peticion.nota().setScale(2));
        calificacion.setComentario(peticion.comentario());
        calificacion.setActualizadoPor(usuarioId);
        Calificacion guardada = repositorio.save(calificacion);

        auditar("ACTUALIZAR", guardada, usuarioId, direccionIp);
        return aRespuesta(guardada);
    }

    /**
     * Consulta paginada con filtros combinables: estudianteId, asignacionId,
     * materiaId, grupoId, anioId y periodoId. Las combinaciones se resuelven
     * con los metodos derivados del repositorio y el conteo lo hace la base
     * (nunca se carga toda la tabla).
     */
    @Transactional(readOnly = true)
    public PaginaRespuesta<CalificacionRespuesta> listar(Long estudianteId, Long asignacionId,
            Long materiaId, Long grupoId, Long anioId, Long periodoId, Pageable pageable) {
        Page<Calificacion> filas;
        if (estudianteId != null && periodoId != null) {
            filas = repositorio
                    .findByEstudianteIdAndPeriodoIdOrderByMateriaIdAscNumeroEvaluacionAsc(
                            estudianteId, periodoId, pageable);
        } else if (estudianteId != null) {
            filas = repositorio
                    .findByEstudianteIdOrderByAnioIdAscMateriaIdAscNumeroEvaluacionAsc(
                            estudianteId, pageable);
        } else if (materiaId != null && periodoId != null) {
            filas = repositorio
                    .findByMateriaIdAndPeriodoIdOrderByEstudianteIdAscNumeroEvaluacionAsc(
                            materiaId, periodoId, pageable);
        } else if (asignacionId != null) {
            filas = repositorio.findByAsignacionIdOrderByNumeroEvaluacionAsc(asignacionId,
                    pageable);
        } else if (materiaId != null) {
            filas = repositorio.findByMateriaIdOrderByAnioIdAscGrupoIdAscEstudianteIdAsc(
                    materiaId, pageable);
        } else if (grupoId != null) {
            filas = repositorio
                    .findByGrupoIdOrderByMateriaIdAscEstudianteIdAscNumeroEvaluacionAsc(
                            grupoId, pageable);
        } else if (anioId != null) {
            filas = repositorio.findByAnioIdOrderByGrupoIdAscMateriaIdAscEstudianteIdAsc(
                    anioId, pageable);
        } else if (periodoId != null) {
            filas = repositorio
                    .findByPeriodoIdOrderByEstudianteIdAscMateriaIdAscNumeroEvaluacionAsc(
                            periodoId, pageable);
        } else {
            filas = repositorio.findAll(
                    pageable.getSort().isSorted() ? pageable : PageRequest.of(
                            pageable.getPageNumber(), pageable.getPageSize(),
                            Sort.by(Sort.Direction.ASC,
                                    "anioId", "materiaId", "estudianteId", "numeroEvaluacion")));
        }
        return PaginaRespuesta.de(filas, CalificacionServicio::aRespuesta);
    }

    /** Consulta una calificacion por id. */
    @Transactional(readOnly = true)
    public CalificacionRespuesta buscarPorId(Long id) {
        return aRespuesta(buscarEntidad(id));
    }

    /**
     * Espacios de evaluacion libres (1..10) de un estudiante en una
     * asignacion: los numeros sin nota registrada. Resuelto con UNA sola
     * consulta (todas las notas del estudiante en la materia y periodo) en
     * lugar de diez consultas por numero.
     */
    @Transactional(readOnly = true)
    public List<Integer> pendientes(Long estudianteId, Long asignacionId) {
        Asignacion asignacion = asignaciones.findById(asignacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una asignacion con id " + asignacionId));

        List<Integer> ocupados = repositorio
                .findByEstudianteIdAndMateriaIdAndPeriodoId(
                        estudianteId, asignacion.getMateriaId(), asignacion.getPeriodoId())
                .stream()
                .map(Calificacion::getNumeroEvaluacion)
                .distinct()
                .toList();

        List<Integer> libres = new ArrayList<>();
        for (int numero = 1; numero <= 10; numero++) {
            if (!ocupados.contains(numero)) {
                libres.add(numero);
            }
        }
        return libres;
    }

    /** Resuelve el profesor dueño de la sesion (404 si la cuenta no es docente). */
    private Profesor profesorDeLaSesion(Long usuarioId) {
        return profesores.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La cuenta autenticada no esta vinculada a un profesor"));
    }

    /** Carga la asignacion y verifica que el profesor de la sesion sea su titular. */
    private Asignacion asignacionDelProfesor(Long asignacionId, Profesor profesor) {
        Asignacion asignacion = asignaciones.findById(asignacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una asignacion con id " + asignacionId));
        if (!esTitular(asignacion, profesor)) {
            throw new AccessDeniedException(
                    "Solo el profesor titular de la asignacion puede registrar o editar "
                            + "calificaciones en ella");
        }
        if (asignacion.getEstado() != EstadoAsignacion.ACTIVA) {
            throw new ReglaNegocioException(
                    "La asignacion no esta ACTIVA (estado actual: " + asignacion.getEstado()
                            + "): no admite calificaciones");
        }
        return asignacion;
    }

    private boolean esTitular(Asignacion asignacion, Profesor profesor) {
        return asignacion.getProfesorId().equals(profesor.getId());
    }

    private Calificacion buscarEntidad(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una calificacion con id " + id));
    }

    private void auditar(String accion, Calificacion calificacion, Long usuarioId,
            String direccionIp) {
        try {
            auditoria.registrar(accion, ENTIDAD, String.valueOf(calificacion.getId()),
                    usuarioId, Map.of(
                            "numeroEvaluacion", calificacion.getNumeroEvaluacion(),
                            "nota", calificacion.getNota()),
                    direccionIp);
        } catch (Exception ignorada) {
            // fail-open: el registro de auditoria nunca bloquea la operacion
        }
    }

    static CalificacionRespuesta aRespuesta(Calificacion c) {
        return new CalificacionRespuesta(
                c.getId(),
                c.getMatriculaId(),
                c.getEstudianteId(),
                c.getAsignacionId(),
                c.getMateriaId(),
                c.getGrupoId(),
                c.getAnioId(),
                c.getPeriodoId(),
                c.getNumeroEvaluacion(),
                c.getNota(),
                c.getComentario(),
                c.getFechaRegistro(),
                c.getRegistradoPor(),
                c.getFechaActualizacion(),
                c.getActualizadoPor());
    }
}
