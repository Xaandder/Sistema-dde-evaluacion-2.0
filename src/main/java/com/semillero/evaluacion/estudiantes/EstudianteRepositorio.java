package com.semillero.evaluacion.estudiantes;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/**
 * Repositorio de ESTUDIANTES. Sin DELETE: la salida es un cambio de ESTADO
 * (borrado logico; la base lo prohibe con TR_ESTUDIANTES_NODELETE).
 */
public interface EstudianteRepositorio extends CrudRepository<Estudiante, Long> {

    boolean existsByDocumento(String documento);

    List<Estudiante> findAllByOrderByApellidosAscNombresAsc();

    List<Estudiante> findByEstadoOrderByApellidosAscNombresAsc(EstadoEstudiante estado);
}
