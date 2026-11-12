package com.semillero.evaluacion.auth;

import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.auth.dto.LoginRespuesta;
import com.semillero.evaluacion.exception.CredencialesInvalidasException;
import com.semillero.evaluacion.exception.CuentaInactivaException;
import com.semillero.evaluacion.exception.ReglaNegocioException;
import com.semillero.evaluacion.seguridad.JwtService;
import com.semillero.evaluacion.usuarios.Usuario;
import com.semillero.evaluacion.usuarios.UsuarioRepositorio;
import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autenticacion: login y cambio obligatorio de contrasena.
 *
 * <p>Auditoria: LOGIN y LOGIN_FALLIDO se registran con transaccion independiente
 * (REQUIRES_NEW dentro de {@link AuditoriaServicio}); la captura de fallos de
 * auditoria ocurre AQUI, fuera del limite transaccional (fail-open aprobado).
 * Nunca se auditan contrasenas, hashes, JWT ni secretos.</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String ENTIDAD_USUARIOS = "USUARIOS";
    private static final String USUARIO_DESCONOCIDO = "USUARIO_DESCONOCIDO";

    private final UsuarioRepositorio usuarios;
    private final AuditoriaServicio auditoria;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UsuarioRepositorio usuarios, AuditoriaServicio auditoria,
            PasswordEncoder encoder, JwtService jwt) {
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional(readOnly = true)
    public LoginRespuesta login(String usuario, String contrasena, String direccionIp) {
        String nombre = usuario == null ? "" : usuario.trim();
        Usuario cuenta = usuarios.findByUsuario(nombre).orElse(null);

        if (cuenta == null) {
            registrarAuditoriaSegura(direccionIp, "LOGIN_FALLIDO", USUARIO_DESCONOCIDO, null,
                    Map.of("usuarioIntentado", nombre, "motivo", "USUARIO_INEXISTENTE"));
            throw new CredencialesInvalidasException();
        }
        if (!Boolean.TRUE.equals(cuenta.getActivo())) {
            registrarAuditoriaSegura(direccionIp, "LOGIN_FALLIDO", nombre, cuenta.getId(),
                    Map.of("motivo", "CUENTA_INACTIVA"));
            throw new CuentaInactivaException();
        }
        if (contrasena == null || !encoder.matches(contrasena, cuenta.getContrasenaHash())) {
            registrarAuditoriaSegura(direccionIp, "LOGIN_FALLIDO", nombre, cuenta.getId(),
                    Map.of("motivo", "CONTRASENA_INCORRECTA"));
            throw new CredencialesInvalidasException();
        }

        registrarAuditoriaSegura(direccionIp, "LOGIN", nombre, cuenta.getId(),
                Map.of("pendienteCambio", Boolean.TRUE.equals(cuenta.getCambioContrasenaPendiente())));
        return respuestaConToken(cuenta);
    }

    /** Cambio de contrasena (obligatorio cuando CAMBIO_CONTRASENA_PENDIENTE=1). */
    @Transactional
    public LoginRespuesta cambiarContrasena(Long usuarioId, String actual,
            String nueva, String confirmacion, String direccionIp) {
        Usuario cuenta = usuarios.findById(usuarioId).orElseThrow(CredencialesInvalidasException::new);

        if (actual == null || !encoder.matches(actual, cuenta.getContrasenaHash())) {
            registrarAuditoriaSegura(direccionIp, "ACTUALIZAR_FALLIDO", cuenta.getUsuario(),
                    cuenta.getId(), Map.of("motivo", "CONTRASENA_ACTUAL_INCORRECTA"));
            throw new CredencialesInvalidasException();
        }
        if (!nueva.equals(confirmacion)) {
            throw new ReglaNegocioException("La confirmacion no coincide con la nueva contrasena");
        }
        validarPolitica(nueva, actual);

        cuenta.setContrasenaHash(encoder.encode(nueva));
        cuenta.setCambioContrasenaPendiente(false);
        cuenta.setActualizadoEn(LocalDateTime.now());
        usuarios.save(cuenta);

        registrarAuditoriaSegura(direccionIp, "ACTUALIZAR", cuenta.getUsuario(), cuenta.getId(),
                Map.of("motivo", "CAMBIO_DE_CONTRASENA"));
        return respuestaConToken(cuenta);
    }

    private void validarPolitica(String nueva, String actual) {
        if (nueva.length() < 8 || nueva.length() > 100) {
            throw new ReglaNegocioException("La nueva contrasena debe tener entre 8 y 100 caracteres");
        }
        boolean tieneLetra = nueva.chars().anyMatch(Character::isLetter);
        boolean tieneNumero = nueva.chars().anyMatch(Character::isDigit);
        if (!tieneLetra || !tieneNumero) {
            throw new ReglaNegocioException("La nueva contrasena debe combinar letras y numeros");
        }
        if (nueva.equals(actual)) {
            throw new ReglaNegocioException("La nueva contrasena debe ser distinta a la actual");
        }
    }

    private LoginRespuesta respuestaConToken(Usuario cuenta) {
        String token = jwt.generarToken(cuenta);
        return new LoginRespuesta(
                token,
                cuenta.getUsuario(),
                cuenta.getRol().name(),
                Boolean.TRUE.equals(cuenta.getCambioContrasenaPendiente()));
    }

    /** Fail-open aprobado: un fallo de auditoria NO rompe el flujo de autenticacion. */
    private void registrarAuditoriaSegura(String ip, String accion, String entidadId,
            Long usuarioId, Map<String, Object> detalle) {
        try {
            auditoria.registrar(accion, ENTIDAD_USUARIOS, entidadId, usuarioId, detalle, ip);
        } catch (Exception e) {
            log.warn("No se pudo registrar el evento de auditoria {}: {}", accion, e.getMessage());
        }
    }
}
