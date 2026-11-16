package com.semillero.evaluacion.solicitudes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Entidad JPA de la tabla SOLICITUDES_VINCULACION (mapa 1:1 con
 * database/database_schema.sql, lineas 520-538). El acudiente pide vincular
 * un estudiante; el ADMINISTRADOR la resuelve (REVISADO_POR) y si la
 * APRUEBA se crea la fila en ESTUDIANTE_ACUDIENTE.
 *
 * <p>La base garantiza: estado PENDIENTE/APROBADA/RECHAZADA
 * (CK_SOL_ESTADO), FK a acudiente/estudiante/usuario y OBSERVACION de hasta
 * 500 caracteres. Java agrega: no resolicitar si ya hay PENDIENTE o si la
 * vinculacion ya existe.</p>
 */
@Entity
@Table(name = "SOLICITUDES_VINCULACION")
public class SolicitudVinculacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ACUDIENTE_ID", nullable = false)
    private Long acudienteId;

    @Column(name = "ESTUDIANTE_ID", nullable = false)
    private Long estudianteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 15)
    private EstadoSolicitud estado;

    @Column(name = "FECHA_CREACION", nullable = false)
    private LocalDateTime fechaCreacion;

    /** USUARIO_ID del ADMINISTRADOR que la resolvio (nullable hasta resolver). */
    @Column(name = "REVISADO_POR")
    private Long revisadoPor;

    @Column(name = "FECHA_RESOLUCION")
    private LocalDateTime fechaResolucion;

    @Column(name = "OBSERVACION", length = 500)
    private String observacion;

    /** Default en Java porque el INSERT de Hibernate envia la columna. */
    @PrePersist
    void alCrear() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getAcudienteId() {
        return acudienteId;
    }

    public void setAcudienteId(Long acudienteId) {
        this.acudienteId = acudienteId;
    }

    public Long getEstudianteId() {
        return estudianteId;
    }

    public void setEstudianteId(Long estudianteId) {
        this.estudianteId = estudianteId;
    }

    public EstadoSolicitud getEstado() {
        return estado;
    }

    public void setEstado(EstadoSolicitud estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public Long getRevisadoPor() {
        return revisadoPor;
    }

    public void setRevisadoPor(Long revisadoPor) {
        this.revisadoPor = revisadoPor;
    }

    public LocalDateTime getFechaResolucion() {
        return fechaResolucion;
    }

    public void setFechaResolucion(LocalDateTime fechaResolucion) {
        this.fechaResolucion = fechaResolucion;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }
}
