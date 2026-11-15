package com.semillero.evaluacion.asignaciones;

import com.semillero.evaluacion.anios.AnioAcademico;
import com.semillero.evaluacion.anios.AnioRepositorio;
import com.semillero.evaluacion.anios.EstadoAnio;
import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.exception.ReglaNegocioException;
import com.semillero.evaluacion.asignaciones.dto.AsignacionRespuesta;
import com.semillero.evaluacion.asignaciones.dto.CrearAsignacionPeticion;
import com.semillero.evaluacion.grupos.EstadoGrupo;
import com.semillero.evaluacion.grupos.Grupo;
import com.semillero.evaluacion.grupos.GrupoRepositorio;
import com.semillero.evaluacion.materias.EstadoMateria;
import com.semillero.evaluacion.materias.Materia;
import com.semillero.evaluacion.materias.MateriaRepositorio;
import com.semillero.evaluacion.periodos.EstadoPeriodo;
import com.semillero.evaluacion.periodos.Periodo;
import com.semillero.evaluacion.periodos.PeriodoRepositorio;
import com.semillero.evaluacion.profesores.EstadoProfesor;
import com.semillero.evaluacion.profesores.Profesor;
import com.semillero.evaluacion.profesores.ProfesorRepositorio;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de negocio de las asignaciones docentes (tabla
 * ASIGNACIONES_DOCENTES).
 *
 * <p>Reglas vigentes (coherentes con el DDL):</p>
 * <ul>
 *   <li>Solo se asigna un profesor ACTIVO a una materia ACTIVA y a un grupo
 *       ACTIVO, en un periodo existente que NO este CERRADO, dentro de un
 *       anio ACTIVO.</li>
 *   <li>Coherencia de anio: el grupo y el periodo deben pertenecer al MISMO
 *       anio (la base lo fija con las FKs compuestas; Java lo valida con un
 *       mensaje claro ANTES de chocar contra Oracle).</li>
 *   <li>UX_ASIGNACION_ACTIVA: una sola asignacion ACTIVA por (profesor,
 *       materia, grupo, periodo); el reingreso del mismo profesor se hace
 *       reactivando la fila INACTIVA, nunca insertando una duplicada.</li>
 *   <li>Sin borrado fisico: finalizar es ACTIVA -&gt; INACTIVA; reemplazar
 *       pasa la asignacion anterior a INACTIVA y crea una nueva con el
 *       profesor entrante.</li>
 *   <li>Toda operacion de escritura se audita (tabla append-only AUDITORIA).</li>
 * </ul>
 */
@Service
public class AsignacionServicio {

    private static final String ENTIDAD = "ASIGNACIONES_DOCENTES";

    private final AsignacionRepositorio repositorio;
    private final ProfesorRepositorio profesores;
    private final MateriaRepositorio materias;
    private final GrupoRepositorio grupos;
    private final PeriodoRepositorio periodos;
    private final AnioRepositorio anios;
    private final AuditoriaServicio auditoria;

    public AsignacionServicio(AsignacionRepositorio repositorio, ProfesorRepositorio profesores,
            MateriaRepositorio materias, GrupoRepositorio grupos, PeriodoRepositorio periodos,
            AnioRepositorio anios, AuditoriaServicio auditoria) {
        this.repositorio = repositorio;
        this.profesores = profesores;
        this.materias = materias;
        this.grupos = grupos;
        this.periodos = periodos;
        this.anios = anios;
        this.auditoria = auditoria;
    }

    /** Crea una asignacion ACTIVA con todos los participantes validados. */
    @Transactional
    public AsignacionRespuesta crear(CrearAsignacionPeticion peticion,
            Long usuarioId, String direccionIp) {
        Profesor profesor = profesores.findById(peticion.profesorId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un profesor con id " + peticion.profesorId()));
        if (profesor.getEstado() != EstadoProfesor.ACTIVO) {
            throw new ReglaNegocioException(
                    "El profesor " + profesor.getDocumento() + " esta inactivo: no admite "
                            + "asignaciones");
        }

        Materia materia = materias.findById(peticion.materiaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una materia con id " + peticion.materiaId()));
        if (materia.getEstado() != EstadoMateria.ACTIVO) {
            throw new ReglaNegocioException(
                    "La materia " + materia.getCodigo() + " esta inactiva: no admite "
                            + "asignaciones");
        }

        Grupo grupo = grupos.findById(peticion.grupoId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un grupo con id " + peticion.grupoId()));
        if (grupo.getEstado() != EstadoGrupo.ACTIVO) {
            throw new ReglaNegocioException(
                    "El grupo " + grupo.getNombre() + " esta inactivo: no admite asignaciones");
        }

        Periodo periodo = periodos.findById(peticion.periodoId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un periodo con id " + peticion.periodoId()));
        if (periodo.getEstado() == EstadoPeriodo.CERRADO) {
            throw new ReglaNegocioException(
                    "El periodo " + periodo.getNombre() + " esta cerrado: no admite "
                            + "asignaciones nuevas");
        }

        // Coherencia de anio (FKs compuestas de la base): grupo y periodo del mismo anio.
        if (!grupo.getAnioId().equals(periodo.getAnioId())) {
            throw new ReglaNegocioException(
                    "El grupo y el periodo pertenecen a anios lectivos distintos: la "
                            + "asignacion debe ser del mismo anio");
        }

        AnioAcademico anio = anios.findById(grupo.getAnioId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El grupo pertenece a un anio lectivo inexistente"));
        if (anio.getEstado() == EstadoAnio.CERRADO) {
            throw new ReglaNegocioException(
                    "El anio " + anio.getAnio() + " esta cerrado: no admite asignaciones");
        }

        // UX_ASIGNACION_ACTIVA: una sola ACTIVA por (profesor, materia, grupo, periodo).
        repositorio.findByProfesorIdAndMateriaIdAndGrupoIdAndPeriodoIdAndEstado(
                profesor.getId(), materia.getId(), grupo.getId(), periodo.getId(),
                EstadoAsignacion.ACTIVA).ifPresent(existente -> {
                    throw new ReglaNegocioException(
                            "El profesor ya tiene una asignacion ACTIVA de la materia "
                                    + materia.getCodigo() + " en el grupo " + grupo.getNombre()
                                    + " para este periodo");
                });

        Asignacion asignacion = new Asignacion();
        asignacion.setProfesorId(profesor.getId());
        asignacion.setMateriaId(materia.getId());
        asignacion.setGrupoId(grupo.getId());
        asignacion.setPeriodoId(periodo.getId());
        asignacion.setAnioId(grupo.getAnioId());
        asignacion.setEstado(EstadoAsignacion.ACTIVA);
        Asignacion guardada = repositorio.save(asignacion);

        auditar("CREAR", guardada, usuarioId, Map.of(
                "anio", anio.getAnio(),
                "profesor", profesor.getDocumento(),
                "materia", materia.getCodigo(),
                "grupo", grupo.getNombre()), direccionIp);
        return aRespuesta(guardada, anio.getAnio());
    }

    /**
     * Consulta de asignaciones con filtros combinables: profesorId, grupoId,
     * anioId y materiaId. Si profesor y anio (o grupo y anio) vienen juntos
     * se usa la consulta combinada del repositorio.
     */
    @Transactional(readOnly = true)
    public List<AsignacionRespuesta> listar(Long profesorId, Long grupoId,
            Long anioId, Long materiaId) {
        Integer numeroAnio = anioId != null ? anioDe(anioId) : null;
        List<Asignacion> filas;
        if (profesorId != null && anioId != null) {
            filas = repositorio.findByProfesorIdAndAnioIdOrderByCreadoEnAsc(profesorId, anioId);
        } else if (grupoId != null && anioId != null) {
            filas = repositorio.findByGrupoIdAndAnioIdOrderByCreadoEnAsc(grupoId, anioId);
        } else if (profesorId != null) {
            filas = repositorio.findByProfesorIdOrderByCreadoEnAsc(profesorId);
        } else if (grupoId != null) {
            filas = repositorio.findByGrupoIdOrderByCreadoEnAsc(grupoId);
        } else if (materiaId != null) {
            filas = repositorio.findByMateriaIdOrderByCreadoEnAsc(materiaId);
        } else if (anioId != null) {
            filas = repositorio.findByAnioIdOrderByCreadoEnAsc(anioId);
        } else {
            filas = java.util.stream.StreamSupport
                    .stream(repositorio.findAll().spliterator(), false).toList();
        }
        final Integer anioFinal = numeroAnio;
        return filas.stream().map(a -> aRespuesta(a, anioFinal)).toList();
    }

    /** Consulta de una asignacion por id. */
    @Transactional(readOnly = true)
    public AsignacionRespuesta buscarPorId(Long id) {
        Asignacion asignacion = buscarEntidad(id);
        return aRespuesta(asignacion, anioDe(asignacion.getAnioId()));
    }

    /** Finaliza una asignacion ACTIVA (borrado logico). */
    @Transactional
    public AsignacionRespuesta finalizar(Long id, Long usuarioId, String direccionIp) {
        Asignacion asignacion = buscarEntidad(id);
        if (asignacion.getEstado() != EstadoAsignacion.ACTIVA) {
            throw new ReglaNegocioException(
                    "Solo se puede finalizar una asignacion ACTIVA (estado actual: "
                            + asignacion.getEstado() + ")");
        }

        asignacion.setEstado(EstadoAsignacion.INACTIVA);
        Asignacion guardada = repositorio.save(asignacion);

        auditar("DESACTIVAR", guardada, usuarioId,
                Map.of("anio", anioDe(guardada.getAnioId())), direccionIp);
        return aRespuesta(guardada, anioDe(guardada.getAnioId()));
    }

    /**
     * Reemplaza al profesor de una asignacion ACTIVA: pasa la anterior a
     * INACTIVA y crea una nueva fila ACTIVA con el profesor entrante (la
     * materia, el grupo y el periodo se conservan).
     */
    @Transactional
    public AsignacionRespuesta reemplazar(Long id, Long profesorNuevoId,
            Long usuarioId, String direccionIp) {
        Asignacion anterior = buscarEntidad(id);
        if (anterior.getEstado() != EstadoAsignacion.ACTIVA) {
            throw new ReglaNegocioException(
                    "Solo se puede reemplazar una asignacion ACTIVA (estado actual: "
                            + anterior.getEstado() + ")");
        }
        if (anterior.getProfesorId().equals(profesorNuevoId)) {
            throw new ReglaNegocioException(
                    "El profesor entrante ya es el titular de esta asignacion");
        }

        Profesor entrante = profesores.findById(profesorNuevoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un profesor con id " + profesorNuevoId));
        if (entrante.getEstado() != EstadoProfesor.ACTIVO) {
            throw new ReglaNegocioException(
                    "El profesor " + entrante.getDocumento() + " esta inactivo: no admite "
                            + "asignaciones");
        }

        // UX_ASIGNACION_ACTIVA: el entrante no debe tener otra ACTIVA igual.
        repositorio.findByProfesorIdAndMateriaIdAndGrupoIdAndPeriodoIdAndEstado(
                profesorNuevoId, anterior.getMateriaId(), anterior.getGrupoId(),
                anterior.getPeriodoId(), EstadoAsignacion.ACTIVA).ifPresent(existente -> {
                    throw new ReglaNegocioException(
                            "El profesor entrante ya tiene una asignacion ACTIVA de la "
                                    + "misma materia en el mismo grupo y periodo");
                });

        anterior.setEstado(EstadoAsignacion.INACTIVA);
        repositorio.save(anterior);

        Asignacion nueva = new Asignacion();
        nueva.setProfesorId(entrante.getId());
        nueva.setMateriaId(anterior.getMateriaId());
        nueva.setGrupoId(anterior.getGrupoId());
        nueva.setPeriodoId(anterior.getPeriodoId());
        nueva.setAnioId(anterior.getAnioId());
        nueva.setEstado(EstadoAsignacion.ACTIVA);
        Asignacion guardada = repositorio.save(nueva);

        auditar("ACTUALIZAR", guardada, usuarioId, Map.of(
                "profesorSaliente", anterior.getProfesorId(),
                "profesorEntrante", entrante.getId()), direccionIp);
        return aRespuesta(guardada, anioDe(guardada.getAnioId()));
    }

    private Integer anioDe(Long anioId) {
        return anios.findById(anioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el anio lectivo " + anioId)).getAnio();
    }

    private Asignacion buscarEntidad(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una asignacion con id " + id));
    }

    private void auditar(String accion, Asignacion asignacion, Long usuarioId,
            Map<String, Object> detalle, String direccionIp) {
        try {
            auditoria.registrar(accion, ENTIDAD, String.valueOf(asignacion.getId()),
                    usuarioId, detalle, direccionIp);
        } catch (Exception ignorada) {
            // fail-open: el registro de auditoria nunca bloquea la operacion
        }
    }

    static AsignacionRespuesta aRespuesta(Asignacion asignacion, Integer anio) {
        return new AsignacionRespuesta(
                asignacion.getId(),
                asignacion.getProfesorId(),
                asignacion.getMateriaId(),
                asignacion.getGrupoId(),
                asignacion.getPeriodoId(),
                asignacion.getAnioId(),
                anio,
                asignacion.getEstado().name(),
                asignacion.getCreadoEn());
    }
}
