package com.semillero.evaluacion.auditoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

/**
 * Correccion MEDIO O-2: AUDITORIA.ENTIDAD_ID es VARCHAR2(50) y el nombre de
 * usuario puede llegar a 100 caracteres. El servicio debe acotar el
 * identificador SIN perder la informacion del evento (valor completo en
 * DETALLE), garantizando que el INSERT nunca falle por ORA-12899.
 */
@ExtendWith(MockitoExtension.class)
class AuditoriaServicioTests {

    @Mock
    private AuditoriaRepositorio repositorio;
    @Captor
    private ArgumentCaptor<Auditoria> captor;

    private AuditoriaServicio servicio;

    @BeforeEach
    void setUp() {
        servicio = new AuditoriaServicio(repositorio, new JsonMapper());
    }

    @Test
    @DisplayName("ENTIDAD_ID de 50 o menos caracteres se guarda sin cambios")
    void entidadIdCortoSeConserva() {
        servicio.registrar("LOGIN", "USUARIOS", "profesor1", 1L,
                Map.of("motivo", "OK"), "1.2.3.4");

        verify(repositorio).save(captor.capture());
        Auditoria guardado = captor.getValue();
        assertEquals("profesor1", guardado.getEntidadId());
        assertEquals(1L, guardado.getUsuarioId());
        assertEquals("LOGIN", guardado.getAccion());
        assertEquals("USUARIOS", guardado.getEntidad());
        assertEquals("1.2.3.4", guardado.getDireccionIp());
        assertNotNull(guardado.getFecha());
        assertTrue(guardado.getDetalle().contains("motivo"));
    }

    @Test
    @DisplayName("Nombre de 80 caracteres de usuario existente: ENTIDAD_ID=USUARIO#ID y nombre completo en DETALLE")
    void entidadIdLargoSeReemplazaPorIdEstable() {
        String nombreLargo = "u".repeat(80);

        servicio.registrar("LOGIN", "USUARIOS", nombreLargo, 7L,
                Map.of("motivo", "LOGIN"), "1.2.3.4");

        verify(repositorio).save(captor.capture());
        Auditoria guardado = captor.getValue();
        assertEquals("USUARIO#7", guardado.getEntidadId(),
                "Un nombre largo de usuario existente debe usar su ID inmutable");
        assertTrue(guardado.getEntidadId().length() <= 50,
                "ENTIDAD_ID nunca puede superar los 50 caracteres");
        assertTrue(guardado.getDetalle().contains("\"entidadIdCompleto\":\"" + nombreLargo + "\""),
                "El valor completo debe conservarse en DETALLE: " + guardado.getDetalle());
        assertEquals(7L, guardado.getUsuarioId());
    }

    @Test
    @DisplayName("Al reemplazar por USUARIO#ID, el motivo original se conserva en DETALLE")
    void reemplazoPreservaElDetalleOriginal() {
        String nombreLargo = "x".repeat(60);

        servicio.registrar("LOGIN_FALLIDO", "USUARIOS", nombreLargo, 7L,
                Map.of("motivo", "CONTRASENA_INCORRECTA"), "1.2.3.4");

        verify(repositorio).save(captor.capture());
        Auditoria guardado = captor.getValue();
        assertEquals("USUARIO#7", guardado.getEntidadId());
        assertTrue(guardado.getDetalle().contains("\"motivo\":\"CONTRASENA_INCORRECTA\""),
                "El motivo original debe seguir presente");
        assertTrue(guardado.getDetalle().contains("\"entidadIdCompleto\":\"" + nombreLargo + "\""));
    }

    @Test
    @DisplayName("Dos usuarios con nombres que comparten los primeros 50 caracteres generan ENTIDAD_ID distintos")
    void usuariosConMismoPrefijoGeneranIdentificadoresDistintos() {
        String prefijoCompartido = "prefijo".repeat(10); // 70 caracteres identicos
        String nombreUno = prefijoCompartido + "AAAA";
        String nombreDos = prefijoCompartido + "BBBB";

        servicio.registrar("LOGIN", "USUARIOS", nombreUno, 7L, Map.of("motivo", "LOGIN"), "1.2.3.4");
        servicio.registrar("LOGIN", "USUARIOS", nombreDos, 8L, Map.of("motivo", "LOGIN"), "1.2.3.4");

        verify(repositorio, org.mockito.Mockito.times(2)).save(captor.capture());
        var primero = captor.getAllValues().get(0);
        var segundo = captor.getAllValues().get(1);
        assertEquals("USUARIO#7", primero.getEntidadId());
        assertEquals("USUARIO#8", segundo.getEntidadId());
        assertTrue(!primero.getEntidadId().equals(segundo.getEntidadId()),
                "Los identificadores no deben colisionar");
        // Cada evento conserva su nombre completo en DETALLE.
        assertTrue(primero.getDetalle().contains("\"entidadIdCompleto\":\"" + nombreUno + "\""));
        assertTrue(segundo.getDetalle().contains("\"entidadIdCompleto\":\"" + nombreDos + "\""));
    }

    @Test
    @DisplayName("Nombre largo sin usuarioId: prefijo acotado a 50 y valor completo en DETALLE")
    void nombreLargoSinUsuarioIdUsaPrefijo() {
        String nombreLargo = "z".repeat(70);

        servicio.registrar("LOGIN_FALLIDO", "USUARIOS", nombreLargo, null,
                Map.of("motivo", "USUARIO_INEXISTENTE"), "1.2.3.4");

        verify(repositorio).save(captor.capture());
        Auditoria guardado = captor.getValue();
        assertEquals(50, guardado.getEntidadId().length());
        assertEquals(nombreLargo.substring(0, 50), guardado.getEntidadId());
        assertTrue(guardado.getDetalle().contains("\"entidadIdCompleto\":\"" + nombreLargo + "\""));
    }

    @Test
    @DisplayName("La marca fija USUARIO_DESCONOCIDO nunca requiere acotado")
    void marcaFijaNoSeAcota() {
        servicio.registrar("LOGIN_FALLIDO", "USUARIOS", "USUARIO_DESCONOCIDO", null,
                Map.of("usuarioIntentado", "u".repeat(100), "motivo", "USUARIO_INEXISTENTE"),
                "1.2.3.4");

        verify(repositorio).save(captor.capture());
        Auditoria guardado = captor.getValue();
        assertEquals("USUARIO_DESCONOCIDO", guardado.getEntidadId());
        assertEquals(19, guardado.getEntidadId().length());
        // El nombre completo intentado (100 caracteres) vive en DETALLE, no en ENTIDAD_ID.
        assertTrue(guardado.getDetalle().contains("u".repeat(100)));
        assertNull(guardado.getUsuarioId());
    }
}
