package com.semillero.evaluacion.profesores;

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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidad JPA de la tabla PROFESORES (mapa 1:1 con
 * database/database_schema.sql, lineas 96-113). Datos personales 1:1 con
 * USUARIOS (la cuenta la crea el servicio con rol PROFESOR, como exige el
 * trigger TR_PROFESORES_ROL). El ID lo genera Oracle.
 */
@Entity
@Table(name = "PROFESORES")
public class Profesor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cuenta de acceso asociada (FK_PROF_USUARIO). Inmutable tras el alta. */
    @Column(name = "USUARIO_ID", nullable = false)
    private Long usuarioId;

    @Column(name = "DOCUMENTO", nullable = false, length = 20)
    private String documento;

    @Column(name = "NOMBRES", nullable = false, length = 100)
    private String nombres;

    @Column(name = "APELLIDOS", nullable = false, length = 100)
    private String apellidos;

    @Column(name = "ESPECIALIDAD", length = 100)
    private String especialidad;

    @Column(name = "CORREO", length = 150)
    private String correo;

    @Column(name = "TELEFONO", length = 30)
    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 15)
    private EstadoProfesor estado;

    @Column(name = "CREADO_EN", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "ACTUALIZADO_EN")
    private LocalDateTime actualizadoEn;

    /** Defaults en Java porque el INSERT de Hibernate envia las columnas. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
        if (estado == null) {
            estado = EstadoProfesor.ACTIVO;
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

    public String getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(String especialidad) {
        this.especialidad = especialidad;
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

    public EstadoProfesor getEstado() {
        return estado;
    }

    public void setEstado(EstadoProfesor estado) {
        this.estado = estado;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }
}
