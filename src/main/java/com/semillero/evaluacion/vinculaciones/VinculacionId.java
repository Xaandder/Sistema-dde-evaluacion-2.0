package com.semillero.evaluacion.vinculaciones;

import java.io.Serializable;
import java.util.Objects;

/**
 * Clave compuesta de ESTUDIANTE_ACUDIENTE (PK_EST_ACUD: ESTUDIANTE_ID,
 * ACUDIENTE_ID). La tabla junction no tiene ID propio.
 */
public class VinculacionId implements Serializable {

    private Long estudianteId;
    private Long acudienteId;

    public VinculacionId() {
    }

    public VinculacionId(Long estudianteId, Long acudienteId) {
        this.estudianteId = estudianteId;
        this.acudienteId = acudienteId;
    }

    public Long getEstudianteId() {
        return estudianteId;
    }

    public Long getAcudienteId() {
        return acudienteId;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof VinculacionId id)) {
            return false;
        }
        return Objects.equals(estudianteId, id.estudianteId)
                && Objects.equals(acudienteId, id.acudienteId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(estudianteId, acudienteId);
    }
}
