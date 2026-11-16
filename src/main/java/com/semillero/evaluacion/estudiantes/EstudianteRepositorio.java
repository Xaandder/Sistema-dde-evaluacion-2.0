package com.semillero.evaluacion.estudiantes;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * Repositorio de ESTUDIANTES. Sin DELETE: la salida es un cambio de ESTADO
 * (borrado logico; la base lo prohibe con TR_ESTUDIANTES_NODELETE).
 *
 * <p>Los listados son paginados (Pageable) para no cargar toda la tabla.</p>
 */
public interface EstudianteRepositorio
        extends CrudRepository<Estudiante, Long>, PagingAndSortingRepository<Estudiante, Long> {

    boolean existsByDocumento(String documento);

    Page<Estudiante> findAllByOrderByApellidosAscNombresAsc(Pageable pageable);

    Page<Estudiante> findByEstadoOrderByApellidosAscNombresAsc(EstadoEstudiante estado,
            Pageable pageable);
}
