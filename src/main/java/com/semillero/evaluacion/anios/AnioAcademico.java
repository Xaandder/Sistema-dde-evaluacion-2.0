package com.semillero.evaluacion.anios;

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
 * Entidad JPA de la tabla ANIOS_ACADEMICOS (mapa 1:1 con
 * database/database_schema.sql, lineas 164-173). El ID lo genera Oracle
 * (GENERATED ALWAYS AS IDENTITY): nunca se asigna manualmente.
 *
 * <p>Nota de integridad: CREADO_EN no tiene actualizacion, solo valor de
 * alta; se asigna desde Java en {@link PrePersist} porque el INSERT de
 * Hibernate envia siempre las columnas (los DEFAULT de la base no aplican).</p>
 *
 * <p>La base garantiza: ANIO unico (UNQ_ANIOS_ANIO), rango 2000-2100
 * (CK_ANIOS_ANIO) y estado ACTIVO/CERRADO (CK_ANIOS_ESTADO). Java repite
 * las validaciones para dar mensajes entendibles antes de chocar contra
 * Oracle.</p>
 */
@Entity
@Table(name = "ANIOS_ACADEMICOS")
public class AnioAcademico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Anio lectivo (ej. 2026). Rango 2000-2100 validado en la base y en los DTOs. */
    @Column(name = "ANIO", nullable = false)
    private Integer anio;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 15)
    private EstadoAnio estado;

    @Column(name = "CREADO_EN", nullable = false)
    private LocalDateTime creadoEn;

    /** Default en Java porque el INSERT de Hibernate envia la columna. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
        if (estado == null) {
            estado = EstadoAnio.ACTIVO;
        }
    }

    public Long getId() {
        return id;
    }

    public Integer getAnio() {
        return anio;
    }

    public void setAnio(Integer anio) {
        this.anio = anio;
    }

    public EstadoAnio getEstado() {
        return estado;
    }

    public void setEstado(EstadoAnio estado) {
        this.estado = estado;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }
}
