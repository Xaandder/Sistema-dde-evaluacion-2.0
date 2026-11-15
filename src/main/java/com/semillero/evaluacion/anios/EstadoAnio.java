package com.semillero.evaluacion.anios;

/**
 * Estados de un anio lectivo (CHECK CK_ANIOS_ESTADO en ANIOS_ACADEMICOS):
 * ACTIVO = anio en curso; CERRADO = anio finalizado (no admite cambios).
 */
public enum EstadoAnio {
    ACTIVO,
    CERRADO
}
