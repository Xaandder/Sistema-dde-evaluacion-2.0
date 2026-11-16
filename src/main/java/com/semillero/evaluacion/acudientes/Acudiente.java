package com.semillero.evaluacion.acudientes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Entidad JPA de la tabla ACUDIENTES (mapa 1:1 con
 * database/database_schema.sql, lineas 73-90). Datos personales 1:1 con
 * USUARIOS: la cuenta la crea el servicio con rol ACUDIENTE, como exige el
 * trigger TR_ACUDIENTES_ROL. El ID lo genera Oracle.
 *
 * <p>La base garantiza: USUARIO_ID unico (UNQ_ACUD_USUARIO, 1:1), DOCUMENTO
 * unico (UNQ_ACUD_DOCUMENTO), estado ACTIVO/INACTIVO y sin borrado fisico
 * (TR_ACUDIENTES_NODELETE). Java repite esas reglas con mensajes
 * entendibles.</p>
 */
@Entity
@Table(name = "ACUDIENTES")
public class Acudiente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cuenta de acceso asociada (FK_ACUD_USUARIO). Inmutable tras el alta. */
    @Column(name = "USUARIO_ID", nullable = false)
    private Long usuarioId;

    @Column(name = "DOCUMENTO", nullable = false, length = 20)
    private String documento;

    @Column(name = "NOMBRES", nullable = false, length = 100)
    private String nombres;

    @Column(name = "APELLIDOS", nullable = false, length = 100)
    private String apellidos;

    /** Parentesco con el estudiante vinculado (ej. MADRE, PADRE, TUTOR). */
    @Column(name = "PARENTESCO", length = 30)
    private String parentesco;

    @Column(name = "CORREO", nullable = false, length = 150)
    private String correo;

    @Column(name = "TELEFONO", length = 30)
    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 15)
    private EstadoAcudiente estado;

    @Column(name = "CREADO_EN", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "ACTUALIZADO_EN")
    private LocalDateTime actualizadoEn;

    /** Default en Java porque el INSERT de Hibernate envia la columna. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }

    /** Sello de ultima edicion desde Java (columna ACTUALIZADO_EN). */
    @PreUpdate
    void alActualizar() {
        actualizadoEn = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public String getParentesco() {
        return parentesco;
    }

    public void setParentesco(String parentesco) {
        this.parentesco = parentesco;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public EstadoAcudiente getEstado() {
        return estado;
    }

    public void setEstado(EstadoAcudiente estado) {
        this.estado = estado;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }
}
