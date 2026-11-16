package com.semillero.evaluacion.calificaciones;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * Repositorio de CALIFICACIONES. Sin DELETE: la nota se registra cuando
 * existe y se edita (NOTA/COMENTARIO); el espacio de evaluacion se comparte
 * entre co-docentes por la UNIQUE (ESTUDIANTE, MATERIA, PERIODO, NUMERO).
 *
 * <p>Los listados son paginados (Pageable) para no cargar toda la tabla.</p>
 */
public interface CalificacionRepositorio
        extends CrudRepository<Calificacion, Long>, PagingAndSortingRepository<Calificacion, Long> {

    /** Espejo Java de UNQ_CALIF_ESTUD_MATERIA_PERIODO (co-docentes comparten espacios). */
    Optional<Calificacion> findByEstudianteIdAndMateriaIdAndPeriodoIdAndNumeroEvaluacion(
            Long estudianteId, Long materiaId, Long periodoId, Integer numeroEvaluacion);

    /** Todas las notas de un estudiante en una materia y periodo (espacios ocupados). */
    List<Calificacion> findByEstudianteIdAndMateriaIdAndPeriodoId(
            Long estudianteId, Long materiaId, Long periodoId);

    Page<Calificacion> findByAsignacionIdOrderByNumeroEvaluacionAsc(Long asignacionId,
            Pageable pageable);

    Page<Calificacion> findByEstudianteIdOrderByAnioIdAscMateriaIdAscNumeroEvaluacionAsc(
            Long estudianteId, Pageable pageable);

    Page<Calificacion> findByEstudianteIdAndPeriodoIdOrderByMateriaIdAscNumeroEvaluacionAsc(
            Long estudianteId, Long periodoId, Pageable pageable);

    Page<Calificacion> findByMateriaIdAndPeriodoIdOrderByEstudianteIdAscNumeroEvaluacionAsc(
            Long materiaId, Long periodoId, Pageable pageable);

    Page<Calificacion> findByMateriaIdOrderByAnioIdAscGrupoIdAscEstudianteIdAsc(Long materiaId,
            Pageable pageable);

    Page<Calificacion> findByGrupoIdOrderByMateriaIdAscEstudianteIdAscNumeroEvaluacionAsc(
            Long grupoId, Pageable pageable);

    Page<Calificacion> findByAnioIdOrderByGrupoIdAscMateriaIdAscEstudianteIdAsc(Long anioId,
            Pageable pageable);

    Page<Calificacion> findByPeriodoIdOrderByEstudianteIdAscMateriaIdAscNumeroEvaluacionAsc(
            Long periodoId, Pageable pageable);
}
