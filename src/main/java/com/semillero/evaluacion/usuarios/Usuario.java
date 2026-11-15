package com.semillero.evaluacion.usuarios;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.NumericBooleanConverter;
import org.hibernate.type.SqlTypes;

/**
 * Entidad JPA de la tabla USUARIOS (mapa 1:1 con database/database_schema.sql,
 * lineas 28-43). Nunca se asigna el ID manualmente: Oracle lo genera con
 * GENERATED ALWAYS AS IDENTITY.
 *
 * <p>Nota de integridad: los DEFAULT de la base (ACTIVO, CAMBIO_CONTRASENA_PENDIENTE,
 * CREADO_EN) solo aplican si la columna se omite en el INSERT, pero Hibernate envia
 * siempre todas las columnas mapeadas. Por eso {@link PrePersist} asigna esos
 * valores en Java.</p>
 */
@Entity
@Table(name = "USUARIOS")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "USUARIO", nullable = false, length = 100)
    private String usuario;

    /** Hash BCrypt (60 caracteres). Nunca texto plano. */
    @Column(name = "CONTRASENA_HASH", nullable = false, length = 100)
    private String contrasenaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROL", nullable = false, length = 20)
    private Rol rol;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Convert(converter = NumericBooleanConverter.class)
    @Column(name = "ACTIVO", nullable = false)
    private Boolean activo;

    /** true: la contrasena es temporal y debe cambiarse en el primer login. */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Convert(converter = NumericBooleanConverter.class)
    @Column(name = "CAMBIO_CONTRASENA_PENDIENTE", nullable = false)
    private Boolean cambioContrasenaPendiente;

    @Column(name = "CREADO_EN", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "ACTUALIZADO_EN")
    private LocalDateTime actualizadoEn;

    /** Defaults en Java porque el INSERT de Hibernate siempre envia las columnas. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
        if (activo == null) {
            activo = true;
        }
        if (cambioContrasenaPendiente == null) {
            cambioContrasenaPendiente = true;
        }
    }

    public Long getId() {
        return id;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getContrasenaHash() {
        return contrasenaHash;
    }

    public void setContrasenaHash(String contrasenaHash) {
        this.contrasenaHash = contrasenaHash;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    public Boolean getCambioContrasenaPendiente() {
        return cambioContrasenaPendiente;
    }

    public void setCambioContrasenaPendiente(Boolean cambioContrasenaPendiente) {
        this.cambioContrasenaPendiente = cambioContrasenaPendiente;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(LocalDateTime actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }
}
