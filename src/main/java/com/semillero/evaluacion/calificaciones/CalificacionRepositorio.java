package com.semillero.evaluacion.calificaciones;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/**
 * Repositorio de CALIFICACIONES. Sin DELETE: la nota se registra cuando
 * existe y se edita (NOTA/COMENTARIO); el espacio de evaluacion se comparte
 * entre co-docentes por la UNIQUE (ESTUDIANTE, MATERIA, PERIODO, NUMERO).
 */
public interface CalificacionRepositorio extends CrudRepository<Calificacion, Long> {

    /** Espejo Java de UNQ_CALIF_ESTUD_MATERIA_PERIODO (co-docentes comparten espacios). */
    Optional<Calificacion> findByEstudianteIdAndMateriaIdAndPeriodoIdAndNumeroEvaluacion(
            Long estudianteId, Long materiaId, Long periodoId, Integer numeroEvaluacion);

    List<Calificacion> findByAsignacionIdOrderByNumeroEvaluacionAsc(Long asignacionId);

    List<Calificacion> findByEstudianteIdOrderByAnioIdAscMateriaIdAscNumeroEvaluacionAsc(
            Long estudianteId);

    List<Calificacion> findByEstudianteIdAndPeriodoIdOrderByMateriaIdAscNumeroEvaluacionAsc(
            Long estudianteId, Long periodoId);

    List<Calificacion> findByMateriaIdAndPeriodoIdOrderByEstudianteIdAscNumeroEvaluacionAsc(
            Long materiaId, Long periodoId);

    List<Calificacion> findByMateriaIdOrderByAnioIdAscGrupoIdAscEstudianteIdAsc(Long materiaId);

    List<Calificacion> findByGrupoIdOrderByMateriaIdAscEstudianteIdAscNumeroEvaluacionAsc(
            Long grupoId);

    List<Calificacion> findByAnioIdOrderByGrupoIdAscMateriaIdAscEstudianteIdAsc(Long anioId);

    List<Calificacion> findByPeriodoIdOrderByEstudianteIdAscMateriaIdAscNumeroEvaluacionAsc(
            Long periodoId);
}
