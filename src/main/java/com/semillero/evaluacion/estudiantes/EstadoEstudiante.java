package com.semillero.evaluacion.estudiantes;

/**
 * Estado de un estudiante (CHECK CK_ESTUD_ESTADO): ACTIVO = puede matricularse;
 * INACTIVO = retirado del sistema (borrado logico, sin borrado fisico, como
 * exige el trigger TR_ESTUDIANTES_NODELETE).
 */
public enum EstadoEstudiante {
    ACTIVO,
    INACTIVO
}
