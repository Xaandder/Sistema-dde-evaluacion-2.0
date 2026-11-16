package com.semillero.evaluacion.matriculas;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * Repositorio de MATRICULAS. Sin DELETE: los cambios son de ESTADO
 * (ACTIVA/RETIRADA/FINALIZADA), protegidos por las UNIQUE y el indice
 * funcional UX_MATRICULA_ACTIVA de la base.
 *
 * <p>Los listados son paginados (Pageable) para no cargar toda la tabla.</p>
 */
public interface MatriculaRepositorio
        extends CrudRepository<Matricula, Long>, PagingAndSortingRepository<Matricula, Long> {

    Optional<Matricula> findByEstudianteIdAndGrupoId(Long estudianteId, Long grupoId);

    /** Maximo una ACTIVA por (estudiante, anio) — espejo de UX_MATRICULA_ACTIVA. */
    Optional<Matricula> findByEstudianteIdAndAnioIdAndEstado(Long estudianteId, Long anioId,
            EstadoMatricula estado);

    Page<Matricula> findByEstudianteIdOrderByFechaMatriculaAsc(Long estudianteId,
            Pageable pageable);

    Page<Matricula> findByAnioIdOrderByEstudianteIdAsc(Long anioId, Pageable pageable);

    Page<Matricula> findByEstudianteIdAndAnioIdOrderByFechaMatriculaAsc(Long estudianteId,
            Long anioId, Pageable pageable);

    /** Lista completa de las matriculas de un estudiante (pocas: una por anio). */
    List<Matricula> findAllByEstudianteIdOrderByFechaMatriculaAsc(Long estudianteId);
}
