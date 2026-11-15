package com.semillero.evaluacion.grupos;

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
 * Entidad JPA de la tabla GRUPOS (mapa 1:1 con database/database_schema.sql,
 * lineas 225-237). Grupos por anio con nombre libre (ej. 10-A); el ID lo
 * genera Oracle (GENERATED ALWAYS AS IDENTITY).
 *
 * <p>La base garantiza: (ANIO_ID, NOMBRE) unico (UNQ_GRUPO_ANIO_NOMBRE) y
 * estado ACTIVO/INACTIVO. La combinacion unica (ID, ANIO_ID) de la base
 * existe para las FKs compuestas de MATRICULAS y ASIGNACIONES_DOCENTES
 * (modulos 5 y 6), que Java no manipula directamente.</p>
 */
@Entity
@Table(name = "GRUPOS")
public class Grupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Anio lectivo del grupo (FK_GRUPO_ANIO). Inmutable tras el alta. */
    @Column(name = "ANIO_ID", nullable = false)
    private Long anioId;

    /** Nombre libre por anio (ej. 10-A). Unico dentro del anio. */
    @Column(name = "NOMBRE", nullable = false, length = 20)
    private String nombre;

    @Column(name = "DESCRIPCION", length = 200)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 15)
    private EstadoGrupo estado;

    @Column(name = "CREADO_EN", nullable = false)
    private LocalDateTime creadoEn;

    /** Defaults en Java porque el INSERT de Hibernate envia las columnas. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
        if (estado == null) {
            estado = EstadoGrupo.ACTIVO;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getAnioId() {
        return anioId;
    }

    public void setAnioId(Long anioId) {
        this.anioId = anioId;
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

    public EstadoGrupo getEstado() {
        return estado;
    }

    public void setEstado(EstadoGrupo estado) {
        this.estado = estado;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }
}
