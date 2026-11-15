package com.semillero.evaluacion.asignaciones;

/**
 * Estado de una asignacion docente (CHECK CK_ASIGNACION_ESTADO): ACTIVA =
 * vigente (el profesor puede registrar calificaciones); INACTIVA =
 * finalizada o reemplazada (borrado logico).
 */
public enum EstadoAsignacion {
    ACTIVA,
    INACTIVA
}
