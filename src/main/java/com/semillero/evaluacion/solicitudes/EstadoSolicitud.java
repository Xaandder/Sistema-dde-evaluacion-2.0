package com.semillero.evaluacion.solicitudes;

/**
 * Estado de una solicitud de vinculacion (CHECK CK_SOL_ESTADO):
 * PENDIENTE = esperando revision del ADMINISTRADOR; APROBADA = crea la
 * vinculacion (fila en ESTUDIANTE_ACUDIENTE); RECHAZADA = denegada con
 * observacion. REVISADO_POR queda con el USUARIO_ID del administrador.
 */
public enum EstadoSolicitud {
    PENDIENTE,
    APROBADA,
    RECHAZADA
}
