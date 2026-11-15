package com.semillero.evaluacion.periodos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidad JPA de la tabla PERIODOS (mapa 1:1 con database/database_schema.sql,
 * lineas 184-201). El ID lo genera Oracle (GENERATED ALWAYS AS IDENTITY).
 *
 * <p>La base garantiza: (ANIO_ID, NUMERO) unico (UNQ_PERIODO_ANIO_NUMERO),
 * NUMERO >= 1, FECHA_FIN >= FECHA_INICIO cuando ambas existen, y el estado
 * PENDIENTE/ABIERTO/CERRADO. Java repite esas reglas con mensajes entendibles
 * y agrega las que SQL no puede expresar (ciclo de vida, anio no cerrado).</p>
 *
 * <p>La combinacion unica (ID, ANIO_ID) de la base existe para que la FK
 * compuesta de ASIGNACIONES_DOCENTES (modulo 6) siempre referencie un
 * periodo del mismo anio: no es una columna que Java manipule.</p>
 */
@Entity
@Table(name = "PERIODOS")
public class Periodo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Anio lectivo al que pertenece (FK_PERIODO_ANIO). Inmutable tras el alta. */
    @Column(name = "ANIO_ID", nullable = false)
    private Long anioId;

    /** Ordinal del periodo dentro del anio (1, 2, 3...). Unico por anio. */
    @Column(name = "NUMERO", nullable = false)
    private Integer numero;

    /** Nombre legible (ej. "Primer periodo"). */
    @Column(name = "NOMBRE", nullable = false, length = 50)
    private String nombre;

    @Column(name = "FECHA_INICIO")
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 10)
    private EstadoPeriodo estado;

    @Column(name = "CREADO_EN", nullable = false)
    private LocalDateTime creadoEn;

    /** Defaults en Java porque el INSERT de Hibernate envia las columnas. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
        if (estado == null) {
            estado = EstadoPeriodo.PENDIENTE;
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

    public Integer getNumero() {
        return numero;
    }

    public void setNumero(Integer numero) {
        this.numero = numero;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDate fechaFin) {
        this.fechaFin = fechaFin;
    }

    public EstadoPeriodo getEstado() {
        return estado;
    }

    public void setEstado(EstadoPeriodo estado) {
        this.estado = estado;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }
}
