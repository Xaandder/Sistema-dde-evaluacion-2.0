package com.semillero.evaluacion.vinculaciones;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/**
 * Repositorio de la junction ESTUDIANTE_ACUDIENTE. La PK compuesta impide
 * vinculaciones duplicadas; Java lo repite con un mensaje entendible.
 */
public interface VinculacionRepositorio extends CrudRepository<EstudianteAcudiente, VinculacionId> {

    Optional<EstudianteAcudiente> findByEstudianteIdAndAcudienteId(Long estudianteId,
            Long acudienteId);

    boolean existsByEstudianteIdAndAcudienteId(Long estudianteId, Long acudienteId);

    List<EstudianteAcudiente> findByAcudienteIdOrderByFechaVinculacionAsc(Long acudienteId);

    List<EstudianteAcudiente> findByEstudianteIdOrderByFechaVinculacionAsc(Long estudianteId);
}
