package com.semillero.evaluacion.materias;

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
 * Entidad JPA de la tabla MATERIAS (mapa 1:1 con database/database_schema.sql,
 * lineas 207-217). Catalogo de asignaturas con codigo unico; el ID lo genera
 * Oracle (GENERATED ALWAYS AS IDENTITY).
 *
 * <p>La base garantiza: CODIGO unico (UNQ_MATERIAS_CODIGO) y estado
 * ACTIVO/INACTIVO (CK_MATERIAS_ESTADO). Java repite esas reglas con mensajes
 * entendibles; la salida del catalogo es un cambio de estado, nunca DELETE.</p>
 */
@Entity
@Table(name = "MATERIAS")
public class Materia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Codigo libre asignado por el administrador (ej. MAT-101). Unico. */
    @Column(name = "CODIGO", nullable = false, length = 20)
    private String codigo;

    @Column(name = "NOMBRE", nullable = false, length = 100)
    private String nombre;

    @Column(name = "DESCRIPCION", length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 15)
    private EstadoMateria estado;

    @Column(name = "CREADO_EN", nullable = false)
    private LocalDateTime creadoEn;

    /** Defaults en Java porque el INSERT de Hibernate envia las columnas. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
        if (estado == null) {
            estado = EstadoMateria.ACTIVO;
        }
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public EstadoMateria getEstado() {
        return estado;
    }

    public void setEstado(EstadoMateria estado) {
        this.estado = estado;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }
}
