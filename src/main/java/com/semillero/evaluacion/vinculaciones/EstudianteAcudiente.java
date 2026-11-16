package com.semillero.evaluacion.vinculaciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Entidad JPA de la tabla junction ESTUDIANTE_ACUDIENTE (mapa 1:1 con
 * database/database_schema.sql, lineas 506-516). Vinculacion AUTORIZADA
 * estudiante-acudiente: la fila ES la autorizacion (no hay columna de
 * estado; la solicitud APROBADA la crea). PK compuesta (ESTUDIANTE_ID,
 * ACUDIENTE_ID): la base impide vinculaciones duplicadas.
 */
@Entity
@Table(name = "ESTUDIANTE_ACUDIENTE")
@IdClass(VinculacionId.class)
public class EstudianteAcudiente {

    @Id
    @Column(name = "ESTUDIANTE_ID", nullable = false)
    private Long estudianteId;

    @Id
    @Column(name = "ACUDIENTE_ID", nullable = false)
    private Long acudienteId;

    @Column(name = "FECHA_VINCULACION", nullable = false)
    private LocalDateTime fechaVinculacion;

    /** Default en Java porque el INSERT de Hibernate envia la columna. */
    @PrePersist
    void alCrear() {
        if (fechaVinculacion == null) {
            fechaVinculacion = LocalDateTime.now();
        }
    }

    public Long getEstudianteId() {
        return estudianteId;
    }

    public void setEstudianteId(Long estudianteId) {
        this.estudianteId = estudianteId;
    }

    public Long getAcudienteId() {
        return acudienteId;
    }

    public void setAcudienteId(Long acudienteId) {
        this.acudienteId = acudienteId;
    }

    public LocalDateTime getFechaVinculacion() {
        return fechaVinculacion;
    }
}
