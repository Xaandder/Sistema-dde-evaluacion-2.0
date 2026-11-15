package com.semillero.evaluacion.matriculas;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/**
 * Repositorio de MATRICULAS. Sin DELETE: los cambios son de ESTADO
 * (ACTIVA/RETIRADA/FINALIZADA), protegidos por las UNIQUE y el indice
 * funcional UX_MATRICULA_ACTIVA de la base.
 */
public interface MatriculaRepositorio extends CrudRepository<Matricula, Long> {

    Optional<Matricula> findByEstudianteIdAndGrupoId(Long estudianteId, Long grupoId);

    /** Maximo una ACTIVA por (estudiante, anio) — espejo de UX_MATRICULA_ACTIVA. */
    Optional<Matricula> findByEstudianteIdAndAnioIdAndEstado(Long estudianteId, Long anioId,
            EstadoMatricula estado);

    List<Matricula> findByEstudianteIdOrderByFechaMatriculaAsc(Long estudianteId);

    List<Matricula> findByAnioIdOrderByEstudianteIdAsc(Long anioId);

    List<Matricula> findByEstudianteIdAndAnioIdOrderByFechaMatriculaAsc(Long estudianteId,
            Long anioId);
}
