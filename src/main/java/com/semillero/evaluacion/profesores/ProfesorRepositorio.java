package com.semillero.evaluacion.profesores;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/**
 * Repositorio de PROFESORES. Sin DELETE: la salida es un cambio de ESTADO
 * (borrado logico; la base lo prohibe con TR_PROFESORES_NODELETE).
 */
public interface ProfesorRepositorio extends CrudRepository<Profesor, Long> {

    boolean existsByDocumento(String documento);

    List<Profesor> findAllByOrderByApellidosAscNombresAsc();

    List<Profesor> findByEstadoOrderByApellidosAscNombresAsc(EstadoProfesor estado);

    /**
     * Localiza el perfil docente vinculado a una cuenta de acceso
     * (PROFESORES.USUARIO_ID = USUARIOS.ID). Se usa para resolver el profesor
     * autenticado (p. ej., al registrar calificaciones con su ASIGNACION_ID).
     */
    Optional<Profesor> findByUsuarioId(Long usuarioId);
}
