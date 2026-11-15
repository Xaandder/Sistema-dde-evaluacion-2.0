package com.semillero.evaluacion.usuarios;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/**
 * Repositorio de USUARIOS. El borrado fisico ya esta prohibido en la base
 * (trigger TR_USUARIOS_NODELETE, error -20006); la desactivacion se hara
 * con ACTIVO=0, nunca con DELETE.
 */
public interface UsuarioRepositorio extends CrudRepository<Usuario, Long> {

    Optional<Usuario> findByUsuario(String usuario);

    Optional<Usuario> findByUsuarioAndRol(String usuario, Rol rol);

    long countByRol(Rol rol);
}
