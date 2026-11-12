package com.semillero.evaluacion.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.semillero.evaluacion.usuarios.Rol;
import com.semillero.evaluacion.usuarios.Usuario;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class JwtServiceTests {

    private static final String SECRETO = "SECRETO-DE-PRUEBAS-CON-MAS-DE-32-CARACTERES";
    private static final String SECRETO_OTRO = "OTRO-SECRETO-DE-PRUEBAS-32-CARACTERES-MAS";

    private final JsonMapper json = new JsonMapper();
    private final JwtService servicio = new JwtService(SECRETO, 60, json);

    private Usuario usuario() {
        Usuario u = new Usuario();
        u.setUsuario("profesor1");
        u.setContrasenaHash("hash-sin-uso");
        u.setRol(Rol.PROFESOR);
        u.setActivo(true);
        u.setCambioContrasenaPendiente(true);
        ReflectionTestUtils.setField(u, "id", 7L);
        return u;
    }

    @Test
    @DisplayName("Genera un token valido y los claims coinciden con el usuario")
    void generaYValidaToken() {
        String token = servicio.generarToken(usuario());

        TokenClaims claims = servicio.validar(token);

        assertEquals("profesor1", claims.usuario());
        assertEquals(Rol.PROFESOR.name(), claims.rol());
        assertTrue(claims.cambioContrasenaPendiente());
        assertTrue(claims.expiracion().isAfter(Instant.now()));
    }

    @Test
    @DisplayName("Un token firmado con otro secreto se rechaza por firma invalida")
    void rechazaFirmaDeOtroSecreto() {
        String token = servicio.generarToken(usuario());
        JwtService otro = new JwtService(SECRETO_OTRO, 60, json);

        assertThrows(JwtInvalidoException.class, () -> otro.validar(token));
    }

    @Test
    @DisplayName("Un token con payload manipulado se rechaza")
    void rechazaPayloadManipulado() {
        String token = servicio.generarToken(usuario());
        String[] partes = token.split("\\.");
        byte[] payloadManipulado = "{\"sub\":\"otro\"}".getBytes(StandardCharsets.UTF_8);
        String tokenManipulado = partes[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payloadManipulado)
                + "." + partes[2];

        assertThrows(JwtInvalidoException.class, () -> servicio.validar(tokenManipulado));
    }

    @Test
    @DisplayName("Un token malformado se rechaza")
    void rechazaTokenMalformado() {
        assertThrows(JwtInvalidoException.class, () -> servicio.validar("abc.def"));
    }

    @Test
    @DisplayName("Un token expirado se rechaza")
    void rechazaTokenExpirado() throws Exception {
        // Token real con exp cambiada a un momento pasado y refirmado con el mismo secreto.
        String token = servicio.generarToken(usuario());
        String[] partes = token.split("\\.");
        Map<String, Object> payload = json.readValue(
                Base64.getUrlDecoder().decode(partes[1]), Map.class);
        payload.put("exp", Instant.now().minusSeconds(60).getEpochSecond());
        String sinFirma = partes[0] + "."
                + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(json.writeValueAsBytes(payload));

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] firma = mac.doFinal(sinFirma.getBytes(StandardCharsets.UTF_8));
        String tokenExpirado = sinFirma + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(firma);

        assertNotEquals(token, tokenExpirado);
        JwtInvalidoException e = assertThrows(JwtInvalidoException.class,
                () -> servicio.validar(tokenExpirado));
        assertEquals("Token expirado", e.getMessage());
    }

    @Test
    @DisplayName("Un secreto corto impide arrancar el servicio (falla segura)")
    void rechazaSecretoCorto() {
        assertThrows(IllegalStateException.class,
                () -> new JwtService("corto", 60, json));
    }
}
