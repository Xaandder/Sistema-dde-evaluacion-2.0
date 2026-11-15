package com.semillero.evaluacion.asignaciones;

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
 * Entidad JPA de la tabla ASIGNACIONES_DOCENTES (mapa 1:1 con
 * database/database_schema.sql, lineas 287-306). Vincula profesor + materia
 * + grupo + periodo, SIEMPRE del mismo anio academico; el ID lo genera
 * Oracle.
 *
 * <p>La base garantiza:</p>
 * <ul>
 *   <li>FK compuesta (GRUPO_ID, ANIO_ID): el grupo pertenece al anio
 *       declarado. FK compuesta (PERIODO_ID, ANIO_ID): el periodo tambien.
 *       Java repite la coherencia con mensajes claros.</li>
 *   <li>UX_ASIGNACION_ACTIVA: una sola asignacion ACTIVA por (profesor,
 *       materia, grupo, periodo); reemplazar al profesor pasa la anterior a
 *       INACTIVA y crea una nueva.</li>
 *   <li>UNQ_ASIGNACION_REF existe para la FK compuesta de CALIFICACIONES
 *       (modulo 7); Java no la manipula.</li>
 * </ul>
 */
@Entity
@Table(name = "ASIGNACIONES_DOCENTES")
public class Asignacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PROFESOR_ID", nullable = false)
    private Long profesorId;

    @Column(name = "MATERIA_ID", nullable = false)
    private Long materiaId;

    @Column(name = "GRUPO_ID", nullable = false)
    private Long grupoId;

    @Column(name = "PERIODO_ID", nullable = false)
    private Long periodoId;

    /** Derivado del grupo y del periodo (que deben coincidir). */
    @Column(name = "ANIO_ID", nullable = false)
    private Long anioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 15)
    private EstadoAsignacion estado;

    @Column(name = "CREADO_EN", nullable = false)
    private LocalDateTime creadoEn;

    /** Defaults en Java porque el INSERT de Hibernate envia las columnas. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
        if (estado == null) {
            estado = EstadoAsignacion.ACTIVA;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getProfesorId() {
        return profesorId;
    }

    public void setProfesorId(Long profesorId) {
        this.profesorId = profesorId;
    }

    public Long getMateriaId() {
        return materiaId;
    }

    public void setMateriaId(Long materiaId) {
        this.materiaId = materiaId;
    }

    public Long getGrupoId() {
        return grupoId;
    }

    public void setGrupoId(Long grupoId) {
        this.grupoId = grupoId;
    }

    public Long getPeriodoId() {
        return periodoId;
    }

    public void setPeriodoId(Long periodoId) {
        this.periodoId = periodoId;
    }

    public Long getAnioId() {
        return anioId;
    }

    public void setAnioId(Long anioId) {
        this.anioId = anioId;
    }

    public EstadoAsignacion getEstado() {
        return estado;
    }

    public void setEstado(EstadoAsignacion estado) {
        this.estado = estado;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }
}
