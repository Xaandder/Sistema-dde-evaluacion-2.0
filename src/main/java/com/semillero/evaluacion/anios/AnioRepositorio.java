package com.semillero.evaluacion.anios;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/**
 * Repositorio de ANIOS_ACADEMICOS. Sin DELETE ni UPDATE casuales: la
 * finalizacion de un anio es un cambio de ESTADO (borrado logico), nunca
 * un borrado fisico.
 */
public interface AnioRepositorio extends CrudRepository<AnioAcademico, Long> {

    Optional<AnioAcademico> findByAnio(Integer anio);

    boolean existsByAnio(Integer anio);

    /** Listado ordenado por anio para la consulta general. */
    List<AnioAcademico> findAllByOrderByAnioAsc();
}
