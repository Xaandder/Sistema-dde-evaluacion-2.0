package com.semillero.evaluacion.grupos;

import com.semillero.evaluacion.anios.AnioAcademico;
import com.semillero.evaluacion.anios.AnioRepositorio;
import com.semillero.evaluacion.anios.EstadoAnio;
import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.exception.ReglaNegocioException;
import com.semillero.evaluacion.grupos.dto.ActualizarGrupoPeticion;
import com.semillero.evaluacion.grupos.dto.CrearGrupoPeticion;
import com.semillero.evaluacion.grupos.dto.GrupoRespuesta;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de negocio de los grupos academicos (tabla GRUPOS).
 *
 * <p>Reglas vigentes:</p>
 * <ul>
 *   <li>Todo grupo nuevo nace ACTIVO y pertenece a un anio ACTIVO: un anio
 *       CERRADO no admite grupos ni cambios en sus grupos.</li>
 *   <li>El nombre es unico dentro del anio (UNQ_GRUPO_ANIO_NOMBRE).</li>
 *   <li>El anio del grupo no se reasigna en edicion (relacion del alta; la
 *       base la fija con las FKs compuestas de MATRICULAS).</li>
 *   <li>La salida del grupo es un cambio de estado (borrado logico): las
 *       matriculas (modulo 5) solo podran apuntar a grupos ACTIVOS.</li>
 *   <li>Toda operacion de escritura se audita (tabla append-only AUDITORIA).</li>
 * </ul>
 */
@Service
public class GrupoServicio {

    private static final String ENTIDAD = "GRUPOS";

    private final GrupoRepositorio repositorio;
    private final AnioRepositorio anios;
    private final AuditoriaServicio auditoria;

    public GrupoServicio(GrupoRepositorio repositorio, AnioRepositorio anios,
            AuditoriaServicio auditoria) {
        this.repositorio = repositorio;
        this.anios = anios;
        this.auditoria = auditoria;
    }

    /** Crea un grupo ACTIVO en un anio lectivo ACTIVO. */
    @Transactional
    public GrupoRespuesta crear(CrearGrupoPeticion peticion, Long usuarioId, String direccionIp) {
        AnioAcademico anio = buscarAnio(peticion.anioId());
        if (anio.getEstado() == EstadoAnio.CERRADO) {
            throw new ReglaNegocioException(
                    "El anio " + anio.getAnio() + " esta cerrado: no admite grupos nuevos");
        }

        String nombre = peticion.nombre().trim();
        if (repositorio.existsByAnioIdAndNombre(anio.getId(), nombre)) {
            throw new ReglaNegocioException("El anio ya tiene un grupo llamado " + nombre);
        }

        Grupo grupo = new Grupo();
        grupo.setAnioId(anio.getId());
        grupo.setNombre(nombre);
        grupo.setDescripcion(peticion.descripcion() != null
                ? peticion.descripcion().trim() : null);
        grupo.setEstado(EstadoGrupo.ACTIVO);
        Grupo guardado = repositorio.save(grupo);

        auditar("CREAR", guardado, usuarioId,
                Map.of("anio", anio.getAnio(), "nombre", guardado.getNombre()), direccionIp);
        return aRespuesta(guardado);
    }

    /** Consulta de grupos; con ?anioId= filtra los de un anio. */
    @Transactional(readOnly = true)
    public List<GrupoRespuesta> listar(Long anioId) {
        if (anioId != null) {
            // Valida que el anio exista (404 si no) y devuelve sus grupos.
            buscarAnio(anioId);
            return repositorio.findByAnioIdOrderByNombreAsc(anioId).stream()
                    .map(GrupoServicio::aRespuesta)
                    .toList();
        }
        return repositorio.findAllByOrderByAnioIdAscNombreAsc().stream()
                .map(GrupoServicio::aRespuesta)
                .toList();
    }

    /** Consulta de un grupo por id. */
    @Transactional(readOnly = true)
    public GrupoRespuesta buscarPorId(Long id) {
        return aRespuesta(buscarEntidad(id));
    }

    /** Modifica nombre y descripcion de un grupo de un anio no cerrado. */
    @Transactional
    public GrupoRespuesta actualizar(Long id, ActualizarGrupoPeticion peticion,
            Long usuarioId, String direccionIp) {
        Grupo grupo = buscarEntidad(id);
        AnioAcademico anio = buscarAnio(grupo.getAnioId());
        if (anio.getEstado() == EstadoAnio.CERRADO) {
            throw new ReglaNegocioException(
                    "El anio " + anio.getAnio() + " esta cerrado: sus grupos no admiten cambios");
        }

        String nombre = peticion.nombre().trim();
        if (!grupo.getNombre().equals(nombre)
                && repositorio.existsByAnioIdAndNombre(grupo.getAnioId(), nombre)) {
            throw new ReglaNegocioException("El anio ya tiene un grupo llamado " + nombre);
        }

        String nombreAnterior = grupo.getNombre();
        grupo.setNombre(nombre);
        grupo.setDescripcion(peticion.descripcion() != null
                ? peticion.descripcion().trim() : null);
        Grupo guardado = repositorio.save(grupo);

        auditar("ACTUALIZAR", guardado, usuarioId, Map.of(
                "nombreAnterior", nombreAnterior, "nombreNuevo", guardado.getNombre()),
                direccionIp);
        return aRespuesta(guardado);
    }

    /** Retira el grupo sin borrarlo (borrado logico). */
    @Transactional
    public GrupoRespuesta desactivar(Long id, Long usuarioId, String direccionIp) {
        Grupo grupo = buscarEntidad(id);
        if (grupo.getEstado() == EstadoGrupo.INACTIVO) {
            throw new ReglaNegocioException(
                    "El grupo " + grupo.getNombre() + " ya esta inactivo");
        }

        grupo.setEstado(EstadoGrupo.INACTIVO);
        Grupo guardado = repositorio.save(grupo);

        auditar("DESACTIVAR", guardado, usuarioId,
                Map.of("nombre", guardado.getNombre()), direccionIp);
        return aRespuesta(guardado);
    }

    /** Reactiva un grupo INACTIVO. */
    @Transactional
    public GrupoRespuesta activar(Long id, Long usuarioId, String direccionIp) {
        Grupo grupo = buscarEntidad(id);
        AnioAcademico anio = buscarAnio(grupo.getAnioId());
        if (anio.getEstado() == EstadoAnio.CERRADO) {
            throw new ReglaNegocioException(
                    "El anio " + anio.getAnio() + " esta cerrado: no se puede reactivar "
                            + "el grupo");
        }
        if (grupo.getEstado() == EstadoGrupo.ACTIVO) {
            throw new ReglaNegocioException(
                    "El grupo " + grupo.getNombre() + " ya esta activo");
        }

        grupo.setEstado(EstadoGrupo.ACTIVO);
        Grupo guardado = repositorio.save(grupo);

        auditar("ACTIVAR", guardado, usuarioId,
                Map.of("nombre", guardado.getNombre()), direccionIp);
        return aRespuesta(guardado);
    }

    /** Anio no existente es 404. */
    private AnioAcademico buscarAnio(Long anioId) {
        return anios.findById(anioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un anio lectivo con id " + anioId));
    }

    private Grupo buscarEntidad(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un grupo con id " + id));
    }

    private void auditar(String accion, Grupo grupo, Long usuarioId,
            Map<String, Object> detalle, String direccionIp) {
        try {
            auditoria.registrar(accion, ENTIDAD, String.valueOf(grupo.getId()),
                    usuarioId, detalle, direccionIp);
        } catch (Exception ignorada) {
            // fail-open: el registro de auditoria nunca bloquea la operacion
        }
    }

    static GrupoRespuesta aRespuesta(Grupo grupo) {
        return new GrupoRespuesta(
                grupo.getId(),
                grupo.getAnioId(),
                grupo.getNombre(),
                grupo.getDescripcion(),
                grupo.getEstado().name(),
                grupo.getCreadoEn());
    }
}
