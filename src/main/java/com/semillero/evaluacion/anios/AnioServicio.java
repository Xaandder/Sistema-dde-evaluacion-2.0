package com.semillero.evaluacion.anios;

import com.semillero.evaluacion.anios.dto.ActualizarAnioPeticion;
import com.semillero.evaluacion.anios.dto.AnioRespuesta;
import com.semillero.evaluacion.anios.dto.CrearAnioPeticion;
import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.exception.ReglaNegocioException;
import com.semillero.evaluacion.periodos.EstadoPeriodo;
import com.semillero.evaluacion.periodos.PeriodoRepositorio;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de negocio de los anios lectivos (tabla ANIOS_ACADEMICOS).
 *
 * <p>Reglas vigentes:</p>
 * <ul>
 *   <li>Todo anio nuevo nace ACTIVO (nada de estados personalizados).</li>
 *   <li>El numero de anio es unico (UNQ_ANIOS_ANIO) y 2000-2100.</li>
 *   <li>Un anio CERRADO queda inmutable: no se modifica ni se reabre
 *       su informacion con este servicio (historico academico).</li>
 *   <li>Sin borrado fisico: la finalizacion es un cambio de estado.</li>
 *   <li>Toda operacion de escritura se audita (tabla append-only AUDITORIA).</li>
 * </ul>
 */
@Service
public class AnioServicio {

    private static final String ENTIDAD = "ANIOS_ACADEMICOS";

    private final AnioRepositorio repositorio;
    private final PeriodoRepositorio periodos;
    private final AuditoriaServicio auditoria;

    public AnioServicio(AnioRepositorio repositorio, PeriodoRepositorio periodos,
            AuditoriaServicio auditoria) {
        this.repositorio = repositorio;
        this.periodos = periodos;
        this.auditoria = auditoria;
    }

    /** Registra un anio lectivo nuevo en estado ACTIVO. */
    @Transactional
    public AnioRespuesta crear(CrearAnioPeticion peticion, Long usuarioId, String direccionIp) {
        if (repositorio.existsByAnio(peticion.anio())) {
            throw new ReglaNegocioException("El anio " + peticion.anio() + " ya esta registrado");
        }

        AnioAcademico anio = new AnioAcademico();
        anio.setAnio(peticion.anio());
        anio.setEstado(EstadoAnio.ACTIVO);
        AnioAcademico guardado = repositorio.save(anio);

        auditar("CREAR", guardado, usuarioId, Map.of("anio", guardado.getAnio()), direccionIp);
        return aRespuesta(guardado);
    }

    /** Consulta de todos los anios, ordenados por numero de anio. */
    @Transactional(readOnly = true)
    public List<AnioRespuesta> listar() {
        return repositorio.findAllByOrderByAnioAsc().stream().map(AnioServicio::aRespuesta).toList();
    }

    /** Consulta de un anio por su identificador. */
    @Transactional(readOnly = true)
    public AnioRespuesta buscarPorId(Long id) {
        return aRespuesta(buscarEntidad(id));
    }

    /**
     * Modifica el numero de un anio ACTIVO. Un anio CERRADO es historico:
     * no se modifica (regla aprobada).
     */
    @Transactional
    public AnioRespuesta actualizar(Long id, ActualizarAnioPeticion peticion,
            Long usuarioId, String direccionIp) {
        AnioAcademico anio = buscarEntidad(id);
        if (anio.getEstado() == EstadoAnio.CERRADO) {
            throw new ReglaNegocioException(
                    "El anio " + anio.getAnio() + " esta cerrado y no admite cambios");
        }

        Integer anterior = anio.getAnio();
        if (!anterior.equals(peticion.anio()) && repositorio.existsByAnio(peticion.anio())) {
            throw new ReglaNegocioException("El anio " + peticion.anio() + " ya esta registrado");
        }

        anio.setAnio(peticion.anio());
        AnioAcademico guardado = repositorio.save(anio);

        auditar("ACTUALIZAR", guardado, usuarioId, Map.of("anioAnterior", anterior,
                "anioNuevo", guardado.getAnio()), direccionIp);
        return aRespuesta(guardado);
    }

    /** Activa un anio (estado ACTIVO). Util para reabrir un anio cerrado por error. */
    @Transactional
    public AnioRespuesta activar(Long id, Long usuarioId, String direccionIp) {
        AnioAcademico anio = buscarEntidad(id);
        if (anio.getEstado() == EstadoAnio.ACTIVO) {
            throw new ReglaNegocioException("El anio " + anio.getAnio() + " ya esta activo");
        }

        anio.setEstado(EstadoAnio.ACTIVO);
        AnioAcademico guardado = repositorio.save(anio);

        auditar("ABRIR", guardado, usuarioId, Map.of("anio", guardado.getAnio()), direccionIp);
        return aRespuesta(guardado);
    }

    /** Finaliza un anio lectivo: queda CERRADO y deja de admitir cambios. */
    @Transactional
    public AnioRespuesta cerrar(Long id, Long usuarioId, String direccionIp) {
        AnioAcademico anio = buscarEntidad(id);
        if (anio.getEstado() == EstadoAnio.CERRADO) {
            throw new ReglaNegocioException("El anio " + anio.getAnio() + " ya esta cerrado");
        }

        // Regla del sistema: no se finaliza un anio con periodos ABIERTO,
        // porque las calificaciones solo se registran en periodos abiertos.
        if (periodos.existsByAnioIdAndEstado(id, EstadoPeriodo.ABIERTO)) {
            throw new ReglaNegocioException(
                    "No se puede cerrar el anio " + anio.getAnio()
                            + ": tiene periodos abiertos");
        }

        anio.setEstado(EstadoAnio.CERRADO);
        AnioAcademico guardado = repositorio.save(anio);

        auditar("CERRAR", guardado, usuarioId, Map.of("anio", guardado.getAnio()), direccionIp);
        return aRespuesta(guardado);
    }

    /** Carga la entidad o lanza 404 con un mensaje entendible. */
    private AnioAcademico buscarEntidad(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un anio lectivo con id " + id));
    }

    /** Audita en su propia transaccion (fail-open en AuditoriaServicio). */
    private void auditar(String accion, AnioAcademico anio, Long usuarioId,
            Map<String, Object> detalle, String direccionIp) {
        try {
            auditoria.registrar(accion, ENTIDAD, String.valueOf(anio.getId()),
                    usuarioId, detalle, direccionIp);
        } catch (Exception ignorada) {
            // fail-open: el registro de auditoria nunca bloquea la operacion
        }
    }

    static AnioRespuesta aRespuesta(AnioAcademico anio) {
        return new AnioRespuesta(
                anio.getId(),
                anio.getAnio(),
                anio.getEstado().name(),
                anio.getCreadoEn());
    }
}
