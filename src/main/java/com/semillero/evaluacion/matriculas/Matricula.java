package com.semillero.evaluacion.matriculas;

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
 * Entidad JPA de la tabla MATRICULAS (mapa 1:1 con
 * database/database_schema.sql, lineas 250-266). Inscribe a un estudiante en
 * un grupo de un anio academico; el ID lo genera Oracle.
 *
 * <p>La base garantiza (y Java lo repite con mensajes claros):</p>
 * <ul>
 *   <li>UNIQUE (ESTUDIANTE_ID, GRUPO_ID): sin matriculas duplicadas en el
 *       mismo grupo. El reingreso al MISMO grupo se hace reactivando la fila
 *       (RETIRADA -> ACTIVA), nunca insertando una nueva.</li>
 *   <li>UX_MATRICULA_ACTIVA: maximo UNA matricula ACTIVA por estudiante y
 *       anio.</li>
 *   <li>FK compuesta (GRUPO_ID, ANIO_ID): el grupo siempre pertenece al ano
 *       declarado (UNQ_MATRICULA_REF existe para la FK compuesta de
 *       CALIFICACIONES del modulo 7; Java no la manipula).</li>
 * </ul>
 */
@Entity
@Table(name = "MATRICULAS")
public class Matricula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ESTUDIANTE_ID", nullable = false)
    private Long estudianteId;

    @Column(name = "GRUPO_ID", nullable = false)
    private Long grupoId;

    /** Derivado del grupo en el alta: el grupo determina el anio. */
    @Column(name = "ANIO_ID", nullable = false)
    private Long anioId;

    @Column(name = "FECHA_MATRICULA", nullable = false)
    private LocalDateTime fechaMatricula;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 15)
    private EstadoMatricula estado;

    /** Default en Java porque el INSERT de Hibernate envia la columna. */
    @PrePersist
    void alCrear() {
        if (fechaMatricula == null) {
            fechaMatricula = LocalDateTime.now();
        }
        if (estado == null) {
            estado = EstadoMatricula.ACTIVA;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getEstudianteId() {
        return estudianteId;
    }

    public void setEstudianteId(Long estudianteId) {
        this.estudianteId = estudianteId;
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

    public LocalDateTime getFechaMatricula() {
        return fechaMatricula;
    }

    public EstadoMatricula getEstado() {
        return estado;
    }

    public void setEstado(EstadoMatricula estado) {
        this.estado = estado;
    }
}
