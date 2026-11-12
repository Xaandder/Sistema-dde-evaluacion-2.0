package com.semillero.evaluacion.auditoria;

import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Escritura de eventos de auditoria.
 *
 * <p>REGLAS APROBADAS (Etapa 3):</p>
 * <ul>
 *   <li>REQUIRES_NEW: cada evento viaja en su propia transaccion, confirmada
 *       aunque la transaccion llamante haga rollback (el registro no se pierde).</li>
 *   <li>fail-open: si la escritura falla, el error se registra en el log
 *       tecnico sin secretos y NO se propaga al flujo llamante (la captura se
 *       hace FUERA del limite transaccional, en el servicio llamante).</li>
 *   <li>FECHA se asigna SIEMPRE desde Java.</li>
 *   <li>Nunca se reciben ni registran contrasenas, hashes, JWT ni secretos.</li>
 * </ul>
 */
@Service
public class AuditoriaServicio {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaServicio.class);

    /** Largo exacto de la columna AUDITORIA.ENTIDAD_ID (VARCHAR2(50)) en Oracle. */
    private static final int LARGO_ENTIDAD_ID = 50;

    private final AuditoriaRepositorio repositorio;
    private final ObjectMapper json;

    public AuditoriaServicio(AuditoriaRepositorio repositorio, ObjectMapper json) {
        this.repositorio = repositorio;
        this.json = json;
    }

    /**
     * Registra un evento. Solo INSERT (ver {@link AuditoriaRepositorio}).
     *
     * @param accion     LOGIN, LOGIN_FALLIDO, CREAR, ACTUALIZAR, ...
     * @param entidad    tabla afectada, p. ej. USUARIOS
     * @param entidadId  identificador de la fila; usar la marca USUARIO_DESCONOCIDO
     *                   cuando el usuario intentado no existe. Si excede los 50
     *                   caracteres de la columna: para un usuario existente se usa
     *                   "USUARIO#<ID>" (estable y unico, el ID es PK inmutable) y
     *                   para otros casos el prefijo de 50; en ambos casos el valor
     *                   completo se conserva en DETALLE ("entidadIdCompleto")
     * @param usuarioId  ID del usuario o null si no existe
     * @param detalle    datos complementarios (se serializan a JSON)
     * @param direccionIp IP del solicitante
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String accion, String entidad, String entidadId,
            Long usuarioId, Map<String, Object> detalle, String direccionIp) {
        // Correccion MEDIO O-2 + colisiones: la columna ENTIDAD_ID es VARCHAR2(50);
        // un nombre de hasta 100 caracteres haria fallar el INSERT (ORA-12899).
        // Ademas, dos nombres distintos que compartan los primeros 50 caracteres
        // producirian el mismo prefijo acotado. Solucion: si hay que acotar y el
        // evento pertenece a un usuario existente, ENTIDAD_ID = "USUARIO#<ID>"
        // (estable y unico); en otros casos se acota al prefijo. El valor
        // completo se preserva SIEMPRE en DETALLE.
        String entidadIdSeguro = entidadId;
        Map<String, Object> detalleFinal = detalle;
        if (entidadId != null && entidadId.length() > LARGO_ENTIDAD_ID) {
            String completo = entidadId;
            if (usuarioId != null) {
                entidadIdSeguro = "USUARIO#" + usuarioId;
            } else {
                entidadIdSeguro = entidadId.substring(0, LARGO_ENTIDAD_ID);
            }
            detalleFinal = detalleAmpliado(detalle, completo);
        }

        Auditoria registro = new Auditoria();
        registro.setAccion(accion);
        registro.setEntidad(entidad);
        registro.setEntidadId(entidadIdSeguro);
        registro.setUsuarioId(usuarioId);
        registro.setDetalle(serializarSeguro(detalleFinal));
        registro.setDireccionIp(direccionIp);
        registro.setFecha(LocalDateTime.now());
        repositorio.save(registro);
    }

    /** Une el detalle original con el identificador completo conservado. */
    private Map<String, Object> detalleAmpliado(Map<String, Object> detalle, String completo) {
        Map<String, Object> ampliado = new java.util.LinkedHashMap<>();
        if (detalle != null) {
            ampliado.putAll(detalle);
        }
        ampliado.put("entidadIdCompleto", completo);
        return ampliado;
    }

    /** Serializacion JSON segura: si falla, guarda un texto plano acotado. */
    private String serializarSeguro(Map<String, Object> detalle) {
        if (detalle == null || detalle.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(detalle);
        } catch (Exception e) {
            log.warn("Detalle de auditoria no serializable a JSON; se guarda texto plano");
            return detalle.toString();
        }
    }
}
