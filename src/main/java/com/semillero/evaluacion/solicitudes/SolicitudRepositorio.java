package com.semillero.evaluacion.solicitudes;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/**
 * Repositorio de SOLICITUDES_VINCULACION. Sin DELETE: la vida de la
 * solicitud es PENDIENTE -&gt; APROBADA/RECHAZADA (resolucion con trazas).
 */
public interface SolicitudRepositorio extends CrudRepository<SolicitudVinculacion, Long> {

    boolean existsByAcudienteIdAndEstudianteIdAndEstado(Long acudienteId, Long estudianteId,
            EstadoSolicitud estado);

    List<SolicitudVinculacion> findAllByOrderByFechaCreacionAsc();

    List<SolicitudVinculacion> findByEstadoOrderByFechaCreacionAsc(EstadoSolicitud estado);

    List<SolicitudVinculacion> findByAcudienteIdOrderByFechaCreacionAsc(Long acudienteId);

    List<SolicitudVinculacion> findByEstudianteIdOrderByFechaCreacionAsc(Long estudianteId);

    List<SolicitudVinculacion> findByAcudienteIdAndEstadoOrderByFechaCreacionAsc(
            Long acudienteId, EstadoSolicitud estado);

    List<SolicitudVinculacion> findByEstudianteIdAndEstadoOrderByFechaCreacionAsc(
            Long estudianteId, EstadoSolicitud estado);
}
