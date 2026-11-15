package com.semillero.evaluacion.profesores;

/**
 * Estado de un profesor (CHECK CK_PROF_ESTADO): ACTIVO = disponible para
 * asignaciones; INACTIVO = retirado del sistema (borrado logico; la base
 * prohibe DELETE con TR_PROFESORES_NODELETE).
 */
public enum EstadoProfesor {
    ACTIVO,
    INACTIVO
}
