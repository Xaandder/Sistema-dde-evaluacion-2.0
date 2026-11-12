package com.semillero.evaluacion.seguridad;

import com.semillero.evaluacion.usuarios.Rol;
import com.semillero.evaluacion.usuarios.Usuario;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.ObjectMapper;

/**
 * Generacion y validacion de tokens JWT HS256.
 *
 * <p>Implementacion manual (firma HMAC-SHA256 del JDK + Jackson 3) porque la
 * libreria jjwt 0.13.0 solo soporta Jackson 2 y este proyecto usa Spring Boot 4
 * con Jackson 3; depender de jjwt habria arrastrado una segunda version de
 * Jackson al classpath.</p>
 *
 * <p>Formato: BASE64URL(header).BASE64URL(payload).BASE64URL(firma HMAC-SHA256).
 * Claims: sub (usuario), uid (ID de USUARIOS), rol, pend (cambio de contrasena
 * pendiente), iat y exp.</p>
 */
public class JwtService {

    private static final String ALGORITMO = "HmacSHA256";
    private static final int LARGO_MINIMO_SECRETO = 32;

    private final byte[] secreto;
    private final long ttlMinutos;
    private final ObjectMapper json;

    /**
     * @param secreto    secreto compartido; si falta o es corto, la aplicacion
     *                   NO arranca (falla segura, decision aprobada).
     * @param ttlMinutos duracion del token en minutos (480 = 8 horas).
     * @param json       ObjectMapper (Jackson 3) para armar el payload.
     */
    public JwtService(String secreto, long ttlMinutos, ObjectMapper json) {
        if (secreto == null || secreto.length() < LARGO_MINIMO_SECRETO) {
            throw new IllegalStateException(
                    "La variable de entorno JWT_SECRET no esta configurada o tiene menos de "
                            + LARGO_MINIMO_SECRETO + " caracteres. La aplicacion no puede emitir tokens.");
        }
        if (ttlMinutos <= 0) {
            throw new IllegalStateException("app.jwt.ttl-minutos debe ser un numero positivo.");
        }
        this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
        this.ttlMinutos = ttlMinutos;
        this.json = json;
    }

    /** Genera un token firmado para el usuario dado. */
    public String generarToken(Usuario usuario) {
        Instant ahora = Instant.now();
        Instant expiracion = ahora.plusSeconds(ttlMinutos * 60);

        Map<String, Object> encabezado = new LinkedHashMap<>();
        encabezado.put("alg", "HS256");
        encabezado.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", usuario.getUsuario());
        payload.put("uid", usuario.getId());
        payload.put("rol", usuario.getRol().name());
        payload.put("pend", Boolean.TRUE.equals(usuario.getCambioContrasenaPendiente()));
        payload.put("iat", ahora.getEpochSecond());
        payload.put("exp", expiracion.getEpochSecond());

        String parteEncabezado = codificarBase64Url(json.writeValueAsBytes(encabezado));
        String partePayload = codificarBase64Url(json.writeValueAsBytes(payload));
        String sinFirma = parteEncabezado + "." + partePayload;
        return sinFirma + "." + codificarBase64Url(firmar(sinFirma));
    }

    /**
     * Verifica firma y vigencia, y devuelve los claims. Lanza
     * {@link JwtInvalidoException} si el token es malformado, manipulado o expiro.
     */
    public TokenClaims validar(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtInvalidoException("Token vacio");
        }
        String[] partes = token.split("\\.");
        if (partes.length != 3) {
            throw new JwtInvalidoException("Token malformado");
        }

        String sinFirma = partes[0] + "." + partes[1];
        byte[] firmaEsperada = firmar(sinFirma);
        byte[] firmaRecibida;
        try {
            firmaRecibida = Base64.getUrlDecoder().decode(partes[2]);
        } catch (IllegalArgumentException e) {
            throw new JwtInvalidoException("Firma ilegible");
        }
        if (!java.security.MessageDigest.isEqual(firmaEsperada, firmaRecibida)) {
            throw new JwtInvalidoException("Firma invalida");
        }

        Map<String, Object> payload;
        try {
            payload = json.readValue(Base64.getUrlDecoder().decode(partes[1]), Map.class);
        } catch (JwtInvalidoException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new JwtInvalidoException("Payload ilegible");
        }
        long exp = numero(payload.get("exp"));
        if (exp <= Instant.now().getEpochSecond()) {
            throw new JwtInvalidoException("Token expirado");
        }
        String sub = texto(payload.get("sub"));
        if (sub == null || sub.isBlank()) {
            throw new JwtInvalidoException("Token sin usuario");
        }
        Rol rol;
        try {
            rol = Rol.valueOf(texto(payload.get("rol")));
        } catch (IllegalArgumentException e) {
            throw new JwtInvalidoException("Rol desconocido en el token");
        }

        return new TokenClaims(
                sub,
                numero(payload.get("uid")),
                rol.name(),
                Boolean.TRUE.equals(payload.get("pend")),
                Instant.ofEpochSecond(exp));
    }

    private byte[] firmar(String contenido) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secreto, ALGORITMO));
            return mac.doFinal(contenido.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
    }

    private String codificarBase64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private long numero(Object valor) {
        if (valor instanceof Number n) {
            return n.longValue();
        }
        throw new JwtInvalidoException("Claim numerico ausente o ilegible");
    }

    private String texto(Object valor) {
        return valor instanceof String s ? s : null;
    }
}
