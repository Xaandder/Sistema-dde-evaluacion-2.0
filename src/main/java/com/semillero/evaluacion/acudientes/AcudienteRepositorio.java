package com.semillero.evaluacion.acudientes;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/**
 * Repositorio de ACUDIENTES. Sin DELETE: la salida es un cambio de ESTADO
 * (borrado logico; la base lo prohibe con TR_ACUDIENTES_NODELETE).
 */
public interface AcudienteRepositorio extends CrudRepository<Acudiente, Long> {

    boolean existsByDocumento(String documento);

    /** Localiza el perfil de acudiente de la cuenta autenticada (USUARIO_ID 1:1). */
    Optional<Acudiente> findByUsuarioId(Long usuarioId);

    List<Acudiente> findAllByOrderByApellidosAscNombresAsc();

    List<Acudiente> findByEstadoOrderByApellidosAscNombresAsc(EstadoAcudiente estado);
}
