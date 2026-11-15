package com.semillero.evaluacion.asignaciones;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/**
 * Repositorio de ASIGNACIONES_DOCENTES. Sin DELETE: la finalizacion o el
 * reemplazo cambian el ESTADO (ACTIVA -> INACTIVA).
 */
public interface AsignacionRepositorio extends CrudRepository<Asignacion, Long> {

    /** Espejo Java de UX_ASIGNACION_ACTIVA (una sola ACTIVA por combinacion). */
    Optional<Asignacion> findByProfesorIdAndMateriaIdAndGrupoIdAndPeriodoIdAndEstado(
            Long profesorId, Long materiaId, Long grupoId, Long periodoId,
            EstadoAsignacion estado);

    List<Asignacion> findByProfesorIdOrderByCreadoEnAsc(Long profesorId);

    List<Asignacion> findByAnioIdOrderByCreadoEnAsc(Long anioId);

    List<Asignacion> findByGrupoIdOrderByCreadoEnAsc(Long grupoId);

    List<Asignacion> findByMateriaIdOrderByCreadoEnAsc(Long materiaId);

    List<Asignacion> findByProfesorIdAndAnioIdOrderByCreadoEnAsc(Long profesorId, Long anioId);

    List<Asignacion> findByGrupoIdAndAnioIdOrderByCreadoEnAsc(Long grupoId, Long anioId);
}
