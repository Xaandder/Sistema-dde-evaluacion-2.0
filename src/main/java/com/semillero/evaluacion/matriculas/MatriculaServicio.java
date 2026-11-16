package com.semillero.evaluacion.matriculas;

import com.semillero.evaluacion.anios.AnioAcademico;
import com.semillero.evaluacion.anios.AnioRepositorio;
import com.semillero.evaluacion.anios.EstadoAnio;
import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.exception.ReglaNegocioException;
import com.semillero.evaluacion.estudiantes.EstadoEstudiante;
import com.semillero.evaluacion.estudiantes.Estudiante;
import com.semillero.evaluacion.estudiantes.EstudianteRepositorio;
import com.semillero.evaluacion.grupos.EstadoGrupo;
import com.semillero.evaluacion.grupos.Grupo;
import com.semillero.evaluacion.grupos.GrupoRepositorio;
import com.semillero.evaluacion.comun.PaginaRespuesta;
import com.semillero.evaluacion.matriculas.dto.CrearMatriculaPeticion;
import com.semillero.evaluacion.matriculas.dto.MatriculaRespuesta;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de negocio de las matriculas (tabla MATRICULAS).
 *
 * <p>Reglas vigentes (coherentes con el DDL):</p>
 * <ul>
 *   <li>Solo se matricula a un estudiante ACTIVO en un grupo ACTIVO de un
 *       anio ACTIVO (un anio cerrado no admite matriculas nuevas).</li>
 *   <li>Sin duplicados: si ya existe una fila del (estudiante, grupo):
 *       ACTIVA -> rechazo; RETIRADA -> reactivacion (RETIRADA -> ACTIVA,
 *       regla del DDL); FINALIZADA -> rechazo.</li>
 *   <li>Maximo UNA matricula ACTIVA por (estudiante, anio), espejo Java de
 *       UX_MATRICULA_ACTIVA: para cambiar de grupo hay que retirar primero.</li>
 *   <li>El anio se deriva SIEMPRE del grupo (la base lo fija con la FK
 *       compuesta; nunca se acepta del cliente).</li>
 *   <li>Sin borrado fisico: retirar es ACTIVA -> RETIRADA.</li>
 *   <li>Toda operacion de escritura se audita (tabla append-only AUDITORIA).</li>
 * </ul>
 */
@Service
public class MatriculaServicio {

    private static final String ENTIDAD = "MATRICULAS";

    private final MatriculaRepositorio repositorio;
    private final EstudianteRepositorio estudiantes;
    private final GrupoRepositorio grupos;
    private final AnioRepositorio anios;
    private final AuditoriaServicio auditoria;

    public MatriculaServicio(MatriculaRepositorio repositorio, EstudianteRepositorio estudiantes,
            GrupoRepositorio grupos, AnioRepositorio anios, AuditoriaServicio auditoria) {
        this.repositorio = repositorio;
        this.estudiantes = estudiantes;
        this.grupos = grupos;
        this.anios = anios;
        this.auditoria = auditoria;
    }

    /** Matricula a un estudiante ACTIVO en un grupo ACTIVO de un anio ACTIVO. */
    @Transactional
    public MatriculaRespuesta crear(CrearMatriculaPeticion peticion,
            Long usuarioId, String direccionIp) {
        Estudiante estudiante = estudiantes.findById(peticion.estudianteId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un estudiante con id " + peticion.estudianteId()));
        if (estudiante.getEstado() != EstadoEstudiante.ACTIVO) {
            throw new ReglaNegocioException(
                    "El estudiante " + estudiante.getDocumento() + " esta inactivo: no admite "
                            + "matriculas");
        }

        Grupo grupo = grupos.findById(peticion.grupoId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un grupo con id " + peticion.grupoId()));
        if (grupo.getEstado() != EstadoGrupo.ACTIVO) {
            throw new ReglaNegocioException(
                    "El grupo " + grupo.getNombre() + " esta inactivo: no admite matriculas");
        }

        AnioAcademico anio = anios.findById(grupo.getAnioId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El grupo pertenece a un anio lectivo inexistente"));
        if (anio.getEstado() == EstadoAnio.CERRADO) {
            throw new ReglaNegocioException(
                    "El anio " + anio.getAnio() + " esta cerrado: no admite matriculas nuevas");
        }

        // Regla de unicidad por (estudiante, grupo) y reactivacion del DDL.
        Matricula existente =
                repositorio.findByEstudianteIdAndGrupoId(estudiante.getId(), grupo.getId())
                        .orElse(null);
        if (existente != null) {
            switch (existente.getEstado()) {
                case ACTIVA -> throw new ReglaNegocioException(
                        "El estudiante ya esta matriculado en el grupo " + grupo.getNombre());
                case FINALIZADA -> throw new ReglaNegocioException(
                        "El estudiante ya finalizo su matricula en el grupo "
                                + grupo.getNombre());
                case RETIRADA -> {
                    // Reingreso al mismo grupo del mismo anio: reactivar la fila.
                    validarUnaActivaPorAnio(estudiante.getId(), grupo.getAnioId(), existente.getId());
                    existente.setEstado(EstadoMatricula.ACTIVA);
                    Matricula reactivada = repositorio.save(existente);
                    auditar("ACTIVAR", reactivada, usuarioId, Map.of(
                            "anio", anio.getAnio(), "documento", estudiante.getDocumento(),
                            "grupo", grupo.getNombre(), "reactivada", true), direccionIp);
                    return aRespuesta(reactivada, anio.getAnio());
                }
            }
        }

        // Maximo una ACTIVA por (estudiante, anio) en cualquier grupo del anio.
        validarUnaActivaPorAnio(estudiante.getId(), grupo.getAnioId(), null);

        Matricula matricula = new Matricula();
        matricula.setEstudianteId(estudiante.getId());
        matricula.setGrupoId(grupo.getId());
        matricula.setAnioId(grupo.getAnioId());
        matricula.setEstado(EstadoMatricula.ACTIVA);
        Matricula guardada = repositorio.save(matricula);

        auditar("CREAR", guardada, usuarioId, Map.of(
                "anio", anio.getAnio(), "documento", estudiante.getDocumento(),
                "grupo", grupo.getNombre()), direccionIp);
        return aRespuesta(guardada, anio.getAnio());
    }

    /**
     * Consulta PAGINADA de matriculas: por estudiante, por anio, por ambos o
     * todas. El numero del anio se resuelve cuando el filtro lo indica.
     */
    @Transactional(readOnly = true)
    public PaginaRespuesta<MatriculaRespuesta> listar(Long estudianteId, Long anioId,
            Pageable pageable) {
        Page<Matricula> filas;
        Integer numeroAnio = null;
        if (estudianteId != null && anioId != null) {
            numeroAnio = anioDe(anioId);
            filas = repositorio.findByEstudianteIdAndAnioIdOrderByFechaMatriculaAsc(
                    estudianteId, anioId, pageable);
        } else if (estudianteId != null) {
            filas = repositorio.findByEstudianteIdOrderByFechaMatriculaAsc(estudianteId,
                    pageable);
        } else if (anioId != null) {
            numeroAnio = anioDe(anioId);
            filas = repositorio.findByAnioIdOrderByEstudianteIdAsc(anioId, pageable);
        } else {
            filas = repositorio.findAll(
                    pageable.getSort().isSorted() ? pageable : PageRequest.of(
                            pageable.getPageNumber(), pageable.getPageSize(),
                            Sort.by(Sort.Direction.ASC, "anioId", "estudianteId")));
        }
        final Integer anioFinal = numeroAnio;
        return PaginaRespuesta.de(filas, m -> aRespuesta(m, anioFinal));
    }

    /**
     * Matriculas de UN estudiante (lista completa: son pocas, una por anio
     * como maximo), para el endpoint anidado /estudiantes/{id}/matriculas.
     */
    @Transactional(readOnly = true)
    public List<MatriculaRespuesta> matriculasDe(Long estudianteId) {
        return repositorio.findAllByEstudianteIdOrderByFechaMatriculaAsc(estudianteId)
                .stream()
                .map(m -> aRespuesta(m, anioDe(m.getAnioId())))
                .toList();
    }

    /** Consulta de una matricula por id. */
    @Transactional(readOnly = true)
    public MatriculaRespuesta buscarPorId(Long id) {
        Matricula matricula = buscarEntidad(id);
        return aRespuesta(matricula, anioDe(matricula.getAnioId()));
    }

    /** Retira la matricula ACTIVA del grupo (borrado logico). */
    @Transactional
    public MatriculaRespuesta retirar(Long id, Long usuarioId, String direccionIp) {
        Matricula matricula = buscarEntidad(id);
        if (matricula.getEstado() != EstadoMatricula.ACTIVA) {
            throw new ReglaNegocioException(
                    "Solo se puede retirar una matricula ACTIVA (estado actual: "
                            + matricula.getEstado() + ")");
        }

        matricula.setEstado(EstadoMatricula.RETIRADA);
        Matricula guardada = repositorio.save(matricula);

        auditar("DESACTIVAR", guardada, usuarioId, Map.of(
                "anio", anioDe(guardada.getAnioId()),
                "grupo", guardada.getGrupoId()), direccionIp);
        return aRespuesta(guardada, anioDe(guardada.getAnioId()));
    }

    /** Rechaza si el estudiante ya tiene otra ACTIVA en el mismo anio. */
    private void validarUnaActivaPorAnio(Long estudianteId, Long anioId, Long excluirId) {
        repositorio.findByEstudianteIdAndAnioIdAndEstado(estudianteId, anioId,
                EstadoMatricula.ACTIVA).ifPresent(otra -> {
                    if (!otra.getId().equals(excluirId)) {
                        throw new ReglaNegocioException(
                                "El estudiante ya tiene una matricula activa en este anio "
                                        + "(grupo id " + otra.getGrupoId()
                                        + "): retirala antes de matricularlo en otro grupo");
                    }
                });
    }

    private Integer anioDe(Long anioId) {
        return anios.findById(anioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el anio lectivo " + anioId)).getAnio();
    }

    private Matricula buscarEntidad(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una matricula con id " + id));
    }

    private void auditar(String accion, Matricula matricula, Long usuarioId,
            Map<String, Object> detalle, String direccionIp) {
        try {
            auditoria.registrar(accion, ENTIDAD, String.valueOf(matricula.getId()),
                    usuarioId, detalle, direccionIp);
        } catch (Exception ignorada) {
            // fail-open: el registro de auditoria nunca bloquea la operacion
        }
    }

    static MatriculaRespuesta aRespuesta(Matricula matricula, Integer anio) {
        return new MatriculaRespuesta(
                matricula.getId(),
                matricula.getEstudianteId(),
                matricula.getGrupoId(),
                matricula.getAnioId(),
                anio,
                matricula.getFechaMatricula(),
                matricula.getEstado().name());
    }
}
