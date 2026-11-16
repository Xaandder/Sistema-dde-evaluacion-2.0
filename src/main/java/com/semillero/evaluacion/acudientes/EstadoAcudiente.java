package com.semillero.evaluacion.acudientes;

/**
 * Estado de un acudiente (CHECK CK_ACUD_ESTADO): ACTIVO = puede solicitar y
 * mantener vinculaciones; INACTIVO = salida logica del sistema (la base lo
 * prohibe fisicamente con TR_ACUDIENTES_NODELETE).
 */
public enum EstadoAcudiente {
    ACTIVO,
    INACTIVO
}
