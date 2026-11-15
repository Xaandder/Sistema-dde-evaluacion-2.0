package com.semillero.evaluacion.grupos;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/**
 * Repositorio de GRUPOS. Sin DELETE ni UPDATE casuales: la salida es un
 * cambio de ESTADO (borrado logico).
 */
public interface GrupoRepositorio extends CrudRepository<Grupo, Long> {

    boolean existsByAnioIdAndNombre(Long anioId, String nombre);

    List<Grupo> findByAnioIdOrderByNombreAsc(Long anioId);

    List<Grupo> findAllByOrderByAnioIdAscNombreAsc();

    List<Grupo> findByAnioIdAndEstadoOrderByNombreAsc(Long anioId, EstadoGrupo estado);
}
