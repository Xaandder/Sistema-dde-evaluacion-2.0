package com.semillero.evaluacion.auditoria;

import org.springframework.data.repository.Repository;

/**
 * Repositorio de AUDITORIA con garantia de INSERT-ONLY a nivel de compilacion.
 *
 * <p>Se extiende la interfaz marcadora {@link Repository} y se expone UNICAMENTE
 * {@code save()}: Java no tiene a su alcance metodos de lectura, actualizacion
 * ni eliminacion (el compilador los rechaza). Como el ID es GENERATED ALWAYS
 * y nunca se asigna, save() siempre ejecuta un INSERT (nunca merge).</p>
 */
public interface AuditoriaRepositorio extends Repository<Auditoria, Long> {

    Auditoria save(Auditoria registro);
}
