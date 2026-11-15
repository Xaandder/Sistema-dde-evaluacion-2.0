package com.semillero.evaluacion.matriculas;

/**
 * Estado de una matricula (CHECK CK_MATRICULA_ESTADO): ACTIVA = inscripcion
 * vigente; RETIRADA = el estudiante dejo el grupo; FINALIZADA = inscripcion
 * cerrada (fin del anio o traslado completo del ciclo).
 */
public enum EstadoMatricula {
    ACTIVA,
    RETIRADA,
    FINALIZADA
}
