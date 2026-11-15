package com.semillero.evaluacion.materias;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/**
 * Repositorio de MATERIAS. Sin DELETE ni UPDATE casuales: la salida del
 * catalogo es un cambio de ESTADO (borrado logico).
 */
public interface MateriaRepositorio extends CrudRepository<Materia, Long> {

    boolean existsByCodigo(String codigo);

    List<Materia> findAllByOrderByNombreAsc();

    List<Materia> findByEstadoOrderByNombreAsc(EstadoMateria estado);
}
