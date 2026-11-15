package com.semillero.evaluacion.periodos;

/**
 * Ciclo de vida de un periodo academico (CHECK CK_PERIODO_ESTADO en PERIODOS):
 * PENDIENTE -> ABIERTO -> CERRADO. Solo en ABIERTO los profesores pueden
 * registrar o editar calificaciones (validado en Java; la base no puede
 * expresarlo).
 */
public enum EstadoPeriodo {
    PENDIENTE,
    ABIERTO,
    CERRADO
}
