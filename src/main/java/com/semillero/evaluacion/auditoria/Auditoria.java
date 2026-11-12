package com.semillero.evaluacion.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Entidad de la tabla AUDITORIA (mapa 1:1 con database/database_schema.sql,
 * lineas 413-424). Append-only: en Java SOLO existe INSERT (ver
 * {@link AuditoriaRepositorio}); la base lo refuerza con el trigger
 * TR_AUDITORIA_SOLOLECTURA (error -20001 contra UPDATE y DELETE).
 *
 * <p>USUARIO_ID es nullable por disenio: los intentos fallidos con usuario
 * inexistente se registran sin vincular a ninguna cuenta.</p>
 */
@Entity
@Table(name = "AUDITORIA")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID del usuario que ejecuto la accion; NULL para intentos con usuario inexistente. */
    @Column(name = "USUARIO_ID")
    private Long usuarioId;

    @Column(name = "ACCION", nullable = false, length = 50)
    private String accion;

    @Column(name = "ENTIDAD", nullable = false, length = 50)
    private String entidad;

    @Column(name = "ENTIDAD_ID", nullable = false, length = 50)
    private String entidadId;

    /** Detalle JSON (por ejemplo, usuario intentado y motivo). */
    @Lob
    @Column(name = "DETALLE")
    private String detalle;

    @Column(name = "DIRECCION_IP", length = 64)
    private String direccionIp;

    /** Asignada SIEMPRE desde Java (decision aprobada): los DEFAULT de la base
     * no aplican porque el INSERT de Hibernate envia todas las columnas. */
    @Column(name = "FECHA", nullable = false)
    private LocalDateTime fecha;

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getAccion() {
        return accion;
    }

    public void setAccion(String accion) {
        this.accion = accion;
    }

    public String getEntidad() {
        return entidad;
    }

    public void setEntidad(String entidad) {
        this.entidad = entidad;
    }

    public String getEntidadId() {
        return entidadId;
    }

    public void setEntidadId(String entidadId) {
        this.entidadId = entidadId;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }

    public String getDireccionIp() {
        return direccionIp;
    }

    public void setDireccionIp(String direccionIp) {
        this.direccionIp = direccionIp;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }
}
