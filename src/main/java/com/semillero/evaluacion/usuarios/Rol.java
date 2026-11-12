package com.semillero.evaluacion.usuarios;

/**
 * Los cuatro roles del sistema, exactamente los mismos valores permitidos por
 * la restriccion CK_USUARIOS_ROL de la tabla USUARIOS. Se guarda como texto
 * (VARCHAR2(20)) gracias a @Enumerated(EnumType.STRING).
 */
public enum Rol {
    ADMINISTRADOR,
    PROFESOR,
    ESTUDIANTE,
    ACUDIENTE
}
