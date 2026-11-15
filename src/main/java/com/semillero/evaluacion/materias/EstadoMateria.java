package com.semillero.evaluacion.materias;

/**
 * Estado de una materia del catalogo (CHECK CK_MATERIAS_ESTADO):
 * ACTIVO = disponible para asignaciones; INACTIVO = retirada del catalogo
 * (borrado logico, sin borrado fisico).
 */
public enum EstadoMateria {
    ACTIVO,
    INACTIVO
}
