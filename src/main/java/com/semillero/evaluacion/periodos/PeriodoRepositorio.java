package com.semillero.evaluacion.periodos;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/**
 * Repositorio de PERIODOS. Sin DELETE ni UPDATE casuales: el ciclo de vida
 * es PENDIENTE -> ABIERTO -> CERRADO (borrado logico, sin borrado fisico).
 */
public interface PeriodoRepositorio extends CrudRepository<Periodo, Long> {

    Optional<Periodo> findByAnioIdAndNumero(Long anioId, Integer numero);

    boolean existsByAnioIdAndNumero(Long anioId, Integer numero);

    /** Usada por AnioServicio: un anio con periodos ABIERTO no se puede cerrar. */
    boolean existsByAnioIdAndEstado(Long anioId, EstadoPeriodo estado);

    List<Periodo> findByAnioIdOrderByNumeroAsc(Long anioId);

    List<Periodo> findAllByOrderByAnioIdAscNumeroAsc();
}
