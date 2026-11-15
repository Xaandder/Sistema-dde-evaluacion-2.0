package com.semillero.evaluacion.grupos;

/**
 * Estado de un grupo academico (CHECK CK_GRUPOS_ESTADO): ACTIVO = disponible
 * para matriculas; INACTIVO = retirado (borrado logico, sin borrado fisico).
 */
public enum EstadoGrupo {
    ACTIVO,
    INACTIVO
}
