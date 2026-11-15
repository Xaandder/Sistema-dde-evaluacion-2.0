package com.semillero.evaluacion.materias;

import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.exception.ReglaNegocioException;
import com.semillero.evaluacion.materias.dto.ActualizarMateriaPeticion;
import com.semillero.evaluacion.materias.dto.CrearMateriaPeticion;
import com.semillero.evaluacion.materias.dto.MateriaRespuesta;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de negocio del catalogo de materias (tabla MATERIAS).
 *
 * <p>Reglas vigentes:</p>
 * <ul>
 *   <li>Todo alta nace ACTIVA y con CODIGO unico (UNQ_MATERIAS_CODIGO).</li>
 *   <li>La salida del catalogo es un cambio de estado (borrado logico):
 *       desactivar mantiene la materia para el historico; activar la vuelve
 *       a dejar disponible para asignaciones.</li>
 *   <li>Las asignaciones (modulo 6) solo podran usar materias ACTIVAS.</li>
 *   <li>Toda operacion de escritura se audita (tabla append-only AUDITORIA).</li>
 * </ul>
 */
@Service
public class MateriaServicio {

    private static final String ENTIDAD = "MATERIAS";

    private final MateriaRepositorio repositorio;
    private final AuditoriaServicio auditoria;

    public MateriaServicio(MateriaRepositorio repositorio, AuditoriaServicio auditoria) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
    }

    /** Registra una materia ACTIVA en el catalogo. */
    @Transactional
    public MateriaRespuesta crear(CrearMateriaPeticion peticion, Long usuarioId, String direccionIp) {
        String codigo = peticion.codigo().trim();
        if (repositorio.existsByCodigo(codigo)) {
            throw new ReglaNegocioException("Ya existe una materia con el codigo " + codigo);
        }

        Materia materia = new Materia();
        materia.setCodigo(codigo);
        materia.setNombre(peticion.nombre().trim());
        materia.setDescripcion(peticion.descripcion() != null
                ? peticion.descripcion().trim() : null);
        materia.setEstado(EstadoMateria.ACTIVO);
        Materia guardada = repositorio.save(materia);

        auditar("CREAR", guardada, usuarioId, Map.of("codigo", guardada.getCodigo()), direccionIp);
        return aRespuesta(guardada);
    }

    /** Consulta del catalogo; con ?estado= filtra por ACTIVO o INACTIVO. */
    @Transactional(readOnly = true)
    public List<MateriaRespuesta> listar(String estado) {
        List<Materia> filas;
        if (estado != null) {
            EstadoMateria estadoValido = estadoValido(estado);
            filas = repositorio.findByEstadoOrderByNombreAsc(estadoValido);
        } else {
            filas = repositorio.findAllByOrderByNombreAsc();
        }
        return filas.stream().map(MateriaServicio::aRespuesta).toList();
    }

    /** Rechaza de forma clara un valor de estado inesperado en el filtro. */
    private EstadoMateria estadoValido(String estado) {
        try {
            return EstadoMateria.valueOf(estado);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException(
                    "El estado debe ser ACTIVO o INACTIVO (recibido: " + estado + ")");
        }
    }

    /** Consulta de una materia por id. */
    @Transactional(readOnly = true)
    public MateriaRespuesta buscarPorId(Long id) {
        return aRespuesta(buscarEntidad(id));
    }

    /** Modifica codigo, nombre y descripcion. Unica regla: codigo no repetido. */
    @Transactional
    public MateriaRespuesta actualizar(Long id, ActualizarMateriaPeticion peticion,
            Long usuarioId, String direccionIp) {
        Materia materia = buscarEntidad(id);
        String codigo = peticion.codigo().trim();

        if (!materia.getCodigo().equals(codigo) && repositorio.existsByCodigo(codigo)) {
            throw new ReglaNegocioException("Ya existe una materia con el codigo " + codigo);
        }

        String codigoAnterior = materia.getCodigo();
        materia.setCodigo(codigo);
        materia.setNombre(peticion.nombre().trim());
        materia.setDescripcion(peticion.descripcion() != null
                ? peticion.descripcion().trim() : null);
        Materia guardada = repositorio.save(materia);

        auditar("ACTUALIZAR", guardada, usuarioId, Map.of(
                "codigoAnterior", codigoAnterior, "codigoNuevo", guardada.getCodigo()),
                direccionIp);
        return aRespuesta(guardada);
    }

    /** Retira la materia del catalogo sin borrarla (borrado logico). */
    @Transactional
    public MateriaRespuesta desactivar(Long id, Long usuarioId, String direccionIp) {
        Materia materia = buscarEntidad(id);
        if (materia.getEstado() == EstadoMateria.INACTIVO) {
            throw new ReglaNegocioException(
                    "La materia " + materia.getCodigo() + " ya esta inactiva");
        }

        materia.setEstado(EstadoMateria.INACTIVO);
        Materia guardada = repositorio.save(materia);

        auditar("DESACTIVAR", guardada, usuarioId,
                Map.of("codigo", guardada.getCodigo()), direccionIp);
        return aRespuesta(guardada);
    }

    /** Reactiva una materia INACTIVA del catalogo. */
    @Transactional
    public MateriaRespuesta activar(Long id, Long usuarioId, String direccionIp) {
        Materia materia = buscarEntidad(id);
        if (materia.getEstado() == EstadoMateria.ACTIVO) {
            throw new ReglaNegocioException(
                    "La materia " + materia.getCodigo() + " ya esta activa");
        }

        materia.setEstado(EstadoMateria.ACTIVO);
        Materia guardada = repositorio.save(materia);

        auditar("ACTIVAR", guardada, usuarioId,
                Map.of("codigo", guardada.getCodigo()), direccionIp);
        return aRespuesta(guardada);
    }

    private Materia buscarEntidad(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una materia con id " + id));
    }

    private void auditar(String accion, Materia materia, Long usuarioId,
            Map<String, Object> detalle, String direccionIp) {
        try {
            auditoria.registrar(accion, ENTIDAD, String.valueOf(materia.getId()),
                    usuarioId, detalle, direccionIp);
        } catch (Exception ignorada) {
            // fail-open: el registro de auditoria nunca bloquea la operacion
        }
    }

    static MateriaRespuesta aRespuesta(Materia materia) {
        return new MateriaRespuesta(
                materia.getId(),
                materia.getCodigo(),
                materia.getNombre(),
                materia.getDescripcion(),
                materia.getEstado().name(),
                materia.getCreadoEn());
    }
}
