package com.semillero.evaluacion.comun;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Envoltorio de paginacion uniforme para los listados de la API.
 *
 * <p>Serializa de forma estable (sin exponer la estructura interna de
 * {@code PageImpl}) y con nombres entendibles en Swagger:</p>
 * <ul>
 *   <li>{@code contenido}: registros de la pagina actual.</li>
 *   <li>{@code pagina}: indice de la pagina (0-based).</li>
 *   <li>{@code tamano}: registros por pagina solicitados.</li>
 *   <li>{@code totalElementos}: registros que cumplen el filtro en total.</li>
 *   <li>{@code totalPaginas}: paginas resultantes con ese tamano.</li>
 * </ul>
 *
 * @param <T> tipo de los registros de la pagina.
 */
public record PaginaRespuesta<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {

    /** Mapea la entidad de la pagina al DTO de respuesta, conservando la metadata. */
    public static <E, T> PaginaRespuesta<T> de(Page<E> page, Function<E, T> mapper) {
        return new PaginaRespuesta<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
