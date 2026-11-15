package com.semillero.evaluacion.periodos;

import com.semillero.evaluacion.anios.AnioAcademico;
import com.semillero.evaluacion.anios.AnioRepositorio;
import com.semillero.evaluacion.anios.EstadoAnio;
import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.exception.RecursoNoEncontradoException;
import com.semillero.evaluacion.exception.ReglaNegocioException;
import com.semillero.evaluacion.periodos.dto.ActualizarPeriodoPeticion;
import com.semillero.evaluacion.periodos.dto.CrearPeriodoPeticion;
import com.semillero.evaluacion.periodos.dto.PeriodoRespuesta;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de negocio de los periodos academicos (tabla PERIODOS).
 *
 * <p>Reglas vigentes:</p>
 * <ul>
 *   <li>Todo periodo nuevo nace PENDIENTE y pertenece al anio indicado.</li>
 *   <li>El anio debe existir y NO estar CERRADO para crear o abrir periodos.</li>
 *   <li>NUMERO es unico dentro del anio (UNQ_PERIODO_ANIO_NUMERO).</li>
 *   <li>Si se registran fechas, FECHA_FIN >= FECHA_INICIO (ademas en la base).</li>
 *   <li>Ciclo de vida estricto: PENDIENTE -> ABIERTO -> CERRADO; CERRADO
 *       queda inmutable.</li>
 *   <li>El anio del periodo no se reasigna en edicion (relacion del alta).</li>
 *   <li>Toda operacion de escritura se audita (tabla append-only AUDITORIA).</li>
 * </ul>
 */
@Service
public class PeriodoServicio {

    private static final String ENTIDAD = "PERIODOS";

    private final PeriodoRepositorio repositorio;
    private final AnioRepositorio anios;
    private final AuditoriaServicio auditoria;

    public PeriodoServicio(PeriodoRepositorio repositorio, AnioRepositorio anios,
            AuditoriaServicio auditoria) {
        this.repositorio = repositorio;
        this.anios = anios;
        this.auditoria = auditoria;
    }

    /** Registra un periodo PENDIENTE asociado al anio lectivo. */
    @Transactional
    public PeriodoRespuesta crear(CrearPeriodoPeticion peticion, Long usuarioId, String direccionIp) {
        AnioAcademico anio = buscarAnio(peticion.anioId());
        if (anio.getEstado() == EstadoAnio.CERRADO) {
            throw new ReglaNegocioException(
                    "El anio " + anio.getAnio() + " esta cerrado: no admite periodos nuevos");
        }
        if (repositorio.existsByAnioIdAndNumero(anio.getId(), peticion.numero())) {
            throw new ReglaNegocioException(
                    "El anio " + anio.getAnio() + " ya tiene un periodo con el numero "
                            + peticion.numero());
        }
        validarFechas(peticion.fechaInicio(), peticion.fechaFin());

        Periodo periodo = new Periodo();
        periodo.setAnioId(anio.getId());
        periodo.setNumero(peticion.numero());
        periodo.setNombre(peticion.nombre().trim());
        periodo.setFechaInicio(peticion.fechaInicio());
        periodo.setFechaFin(peticion.fechaFin());
        periodo.setEstado(EstadoPeriodo.PENDIENTE);
        Periodo guardado = repositorio.save(periodo);

        auditar("CREAR", guardado, usuarioId, Map.of(
                "anio", anio.getAnio(), "numero", guardado.getNumero()), direccionIp);
        return aRespuesta(guardado);
    }

    /** Consulta de periodos; opcionalmente filtrados por anio lectivo. */
    @Transactional(readOnly = true)
    public List<PeriodoRespuesta> listar(Long anioId) {
        if (anioId != null) {
            // Valida que el anio exista (404 si no) y devuelve sus periodos ordenados.
            buscarAnio(anioId);
            return repositorio.findByAnioIdOrderByNumeroAsc(anioId).stream()
                    .map(PeriodoServicio::aRespuesta)
                    .toList();
        }
        return repositorio.findAllByOrderByAnioIdAscNumeroAsc().stream()
                .map(PeriodoServicio::aRespuesta)
                .toList();
    }

    /** Consulta de un periodo por su identificador. */
    @Transactional(readOnly = true)
    public PeriodoRespuesta buscarPorId(Long id) {
        return aRespuesta(buscarEntidad(id));
    }

    /**
     * Modifica numero, nombre y fechas de un periodo NO cerrado. El anio al
     * que pertenece queda fijo desde el alta.
     */
    @Transactional
    public PeriodoRespuesta actualizar(Long id, ActualizarPeriodoPeticion peticion,
            Long usuarioId, String direccionIp) {
        Periodo periodo = buscarEntidad(id);
        if (periodo.getEstado() == EstadoPeriodo.CERRADO) {
            throw new ReglaNegocioException(
                    "El periodo " + periodo.getNombre() + " esta cerrado y no admite cambios");
        }
        AnioAcademico anio = buscarAnio(periodo.getAnioId());
        if (anio.getEstado() == EstadoAnio.CERRADO) {
            throw new ReglaNegocioException(
                    "El anio " + anio.getAnio() + " esta cerrado: sus periodos no admiten cambios");
        }

        if (!periodo.getNumero().equals(peticion.numero())
                && repositorio.existsByAnioIdAndNumero(periodo.getAnioId(), peticion.numero())) {
            throw new ReglaNegocioException(
                    "El anio ya tiene un periodo con el numero " + peticion.numero());
        }
        validarFechas(peticion.fechaInicio(), peticion.fechaFin());

        Integer numeroAnterior = periodo.getNumero();
        periodo.setNumero(peticion.numero());
        periodo.setNombre(peticion.nombre().trim());
        periodo.setFechaInicio(peticion.fechaInicio());
        periodo.setFechaFin(peticion.fechaFin());
        Periodo guardado = repositorio.save(periodo);

        auditar("ACTUALIZAR", guardado, usuarioId, Map.of(
                "numeroAnterior", numeroAnterior, "numeroNuevo", guardado.getNumero()),
                direccionIp);
        return aRespuesta(guardado);
    }

    /** Abre un periodo PENDIENTE (solo en un anio ACTIVO). */
    @Transactional
    public PeriodoRespuesta abrir(Long id, Long usuarioId, String direccionIp) {
        Periodo periodo = buscarEntidad(id);
        if (periodo.getEstado() == EstadoPeriodo.ABIERTO) {
            throw new ReglaNegocioException(
                    "El periodo " + periodo.getNombre() + " ya esta abierto");
        }
        if (periodo.getEstado() == EstadoPeriodo.CERRADO) {
            throw new ReglaNegocioException(
                    "El periodo " + periodo.getNombre() + " esta cerrado y no se puede abrir");
        }

        AnioAcademico anio = buscarAnio(periodo.getAnioId());
        if (anio.getEstado() == EstadoAnio.CERRADO) {
            throw new ReglaNegocioException(
                    "El anio " + anio.getAnio() + " esta cerrado: no se puede abrir el periodo");
        }

        periodo.setEstado(EstadoPeriodo.ABIERTO);
        Periodo guardado = repositorio.save(periodo);

        auditar("ABRIR", guardado, usuarioId,
                Map.of("anio", anio.getAnio(), "numero", guardado.getNumero()), direccionIp);
        return aRespuesta(guardado);
    }

    /** Cierra un periodo ABIERTO: deja de admitir calificaciones (modulo 7). */
    @Transactional
    public PeriodoRespuesta cerrar(Long id, Long usuarioId, String direccionIp) {
        Periodo periodo = buscarEntidad(id);
        if (periodo.getEstado() == EstadoPeriodo.CERRADO) {
            throw new ReglaNegocioException(
                    "El periodo " + periodo.getNombre() + " ya esta cerrado");
        }
        if (periodo.getEstado() == EstadoPeriodo.PENDIENTE) {
            throw new ReglaNegocioException(
                    "El periodo " + periodo.getNombre() + " esta pendiente: solo se "
                            + "cierra un periodo abierto");
        }

        periodo.setEstado(EstadoPeriodo.CERRADO);
        Periodo guardado = repositorio.save(periodo);

        auditar("CERRAR", guardado, usuarioId, Map.of("numero", guardado.getNumero()), direccionIp);
        return aRespuesta(guardado);
    }

    /** Anio no existente es 404; anio cerrado u otra regla es 400. */
    private AnioAcademico buscarAnio(Long anioId) {
        return anios.findById(anioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un anio lectivo con id " + anioId));
    }

    private Periodo buscarEntidad(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un periodo con id " + id));
    }

    private void validarFechas(java.time.LocalDate inicio, java.time.LocalDate fin) {
        if (inicio != null && fin != null && fin.isBefore(inicio)) {
            throw new ReglaNegocioException(
                    "La fecha de fin no puede ser anterior a la fecha de inicio");
        }
    }

    private void auditar(String accion, Periodo periodo, Long usuarioId,
            Map<String, Object> detalle, String direccionIp) {
        try {
            auditoria.registrar(accion, ENTIDAD, String.valueOf(periodo.getId()),
                    usuarioId, detalle, direccionIp);
        } catch (Exception ignorada) {
            // fail-open: el registro de auditoria nunca bloquea la operacion
        }
    }

    static PeriodoRespuesta aRespuesta(Periodo periodo) {
        return new PeriodoRespuesta(
                periodo.getId(),
                periodo.getAnioId(),
                periodo.getNumero(),
                periodo.getNombre(),
                periodo.getFechaInicio(),
                periodo.getFechaFin(),
                periodo.getEstado().name(),
                periodo.getCreadoEn());
    }
}
