package com.semillero.evaluacion.calificaciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad JPA de la tabla CALIFICACIONES (mapa 1:1 con
 * database/database_schema.sql, lineas 372-400). Nota de una evaluacion
 * anclada a una MATRICULA y a una ASIGNACION; el ID lo genera Oracle.
 *
 * <p>Columnas copiadas de sus referencias (MATERIA_ID, GRUPO_ID, ANIO_ID,
 * PERIODO_ID): la base las ancla con dos FKs compuestas y las deja
 * INMUTABLES tras la insercion ({@link CalificacionAsignacionValidator} lo
 * repite en Java). El PERIODO se hereda de la asignacion, no se recibe.</p>
 *
 * <p>La base garantiza: UNIQUE (ESTUDIANTE, MATERIA, PERIODO, NUMERO) — una
 * sola nota por numero de evaluacion, compartida entre co-docentes —,
 * NUMERO_EVALUACION 1..10 y NOTA 0.0..5.0 (NUMBER(4,2)). Java repite esas
 * reglas con mensajes entendibles y agrega las que SQL no puede expresar:
 * matricula ACTIVA, periodo ABIERTO, asignacion ACTIVA y permiso del
 * profesor propietario de la ASIGNACION_ID.</p>
 */
@Entity
@Table(name = "CALIFICACIONES")
public class Calificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "MATRICULA_ID", nullable = false)
    private Long matriculaId;

    /** Derivado de la matricula (FK compuesta FK_CAL_MATRICULA). */
    @Column(name = "ESTUDIANTE_ID", nullable = false)
    private Long estudianteId;

    @Column(name = "ASIGNACION_ID", nullable = false)
    private Long asignacionId;

    /** Derivado de la asignacion (FK compuesta FK_CAL_ASIGNACION); inmutable. */
    @Column(name = "MATERIA_ID", nullable = false)
    private Long materiaId;

    /** Debe coincidir con la matricula Y con la asignacion; inmutable. */
    @Column(name = "GRUPO_ID", nullable = false)
    private Long grupoId;

    /** Debe coincidir con la matricula Y con la asignacion; inmutable. */
    @Column(name = "ANIO_ID", nullable = false)
    private Long anioId;

    /** Heredado de la asignacion (no se repite como dato del cliente). */
    @Column(name = "PERIODO_ID", nullable = false)
    private Long periodoId;

    /** Numero de evaluacion 1..10 (CK_CALIF_NUMERO). Fijo desde el alta. */
    @Column(name = "NUMERO_EVALUACION", nullable = false)
    private Integer numeroEvaluacion;

    /** Nota 0.0..5.0 con dos decimales (CK_CALIF_NOTA, NUMBER(4,2)). Editable. */
    @Column(name = "NOTA", nullable = false, precision = 4, scale = 2)
    private BigDecimal nota;

    @Column(name = "COMENTARIO", length = 500)
    private String comentario;

    @Column(name = "FECHA_REGISTRO", nullable = false)
    private LocalDateTime fechaRegistro;

    /** USUARIO_ID del profesor que la registro (FK_CAL_REGISTRADO_POR). Inmutable. */
    @Column(name = "REGISTRADO_POR", nullable = false)
    private Long registradoPor;

    @Column(name = "FECHA_ACTUALIZACION")
    private LocalDateTime fechaActualizacion;

    /** USUARIO_ID del profesor que la edito por ultima vez (nullable). */
    @Column(name = "ACTUALIZADO_POR")
    private Long actualizadoPor;

    /** Default en Java porque el INSERT de Hibernate envia la columna. */
    @PrePersist
    void alCrear() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
    }

    /** Sello de ultima edicion desde Java (columna FECHA_ACTUALIZACION). */
    @PreUpdate
    void alActualizar() {
        fechaActualizacion = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMatriculaId() {
        return matriculaId;
    }

    public void setMatriculaId(Long matriculaId) {
        this.matriculaId = matriculaId;
    }

    public Long getEstudianteId() {
        return estudianteId;
    }

    public void setEstudianteId(Long estudianteId) {
        this.estudianteId = estudianteId;
    }

    public Long getAsignacionId() {
        return asignacionId;
    }

    public void setAsignacionId(Long asignacionId) {
        this.asignacionId = asignacionId;
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

    public Long getAnioId() {
        return anioId;
    }

    public void setAnioId(Long anioId) {
        this.anioId = anioId;
    }

    public Long getPeriodoId() {
        return periodoId;
    }

    public void setPeriodoId(Long periodoId) {
        this.periodoId = periodoId;
    }

    public Integer getNumeroEvaluacion() {
        return numeroEvaluacion;
    }

    public void setNumeroEvaluacion(Integer numeroEvaluacion) {
        this.numeroEvaluacion = numeroEvaluacion;
    }

    public BigDecimal getNota() {
        return nota;
    }

    public void setNota(BigDecimal nota) {
        this.nota = nota;
    }

    public String getComentario() {
        return comentario;
    }

    public void setComentario(String comentario) {
        this.comentario = comentario;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public Long getRegistradoPor() {
        return registradoPor;
    }

    public void setRegistradoPor(Long registradoPor) {
        this.registradoPor = registradoPor;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public Long getActualizadoPor() {
        return actualizadoPor;
    }

    public void setActualizadoPor(Long actualizadoPor) {
        this.actualizadoPor = actualizadoPor;
    }
}
