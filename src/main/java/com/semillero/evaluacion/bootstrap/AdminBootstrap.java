package com.semillero.evaluacion.bootstrap;

import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.usuarios.Rol;
import com.semillero.evaluacion.usuarios.Usuario;
import com.semillero.evaluacion.usuarios.UsuarioRepositorio;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creacion del PRIMER administrador, con las condiciones aprobadas:
 *
 * <ul>
 *   <li>Solo corre con el perfil "local".</li>
 *   <li>Habilitado explicitamente con la variable ADMIN_BOOTSTRAP_ENABLED=true.</li>
 *   <li>Credenciales por variables de entorno ADMIN_BOOTSTRAP_USUARIO y
 *       ADMIN_BOOTSTRAP_PASSWORD (nunca valores por defecto ni en el repo).</li>
 *   <li>Si ya existe cualquier administrador, NO crea ni sobrescribe nada.</li>
 *   <li>No existe ninguna ruta publica para registrar administradores.</li>
 * </ul>
 */
@Component
@Profile("local")
@ConditionalOnProperty(name = "admin.bootstrap.enabled", havingValue = "true")
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder encoder;
    private final AuditoriaServicio auditoria;

    private final String usuarioInicial;
    private final String contrasenaInicial;

    public AdminBootstrap(UsuarioRepositorio usuarios, PasswordEncoder encoder,
            AuditoriaServicio auditoria,
            @Value("${admin.bootstrap.usuario:}") String usuarioInicial,
            @Value("${admin.bootstrap.password:}") String contrasenaInicial) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.auditoria = auditoria;
        this.usuarioInicial = usuarioInicial;
        this.contrasenaInicial = contrasenaInicial;
    }

    @Override
    public void run(String... args) {
        crearAdministradorInicial();
    }

    /** Logica separada del run() para poder probarla con Mockito. */
    public void crearAdministradorInicial() {
        long existentes = usuarios.countByRol(Rol.ADMINISTRADOR);
        if (existentes > 0) {
            log.info("Ya existen {} administrador(es): no se crea ninguno ni se sobrescribe nada",
                    existentes);
            return;
        }
        if (usuarioInicial == null || usuarioInicial.isBlank()
                || contrasenaInicial == null || contrasenaInicial.isBlank()) {
            log.warn("ADMIN_BOOTSTRAP_ENABLED=true pero faltan ADMIN_BOOTSTRAP_USUARIO o "
                    + "ADMIN_BOOTSTRAP_PASSWORD: no se crea administrador (sin credenciales por defecto)");
            return;
        }

        Usuario admin = new Usuario();
        admin.setUsuario(usuarioInicial.trim());
        admin.setContrasenaHash(encoder.encode(contrasenaInicial));
        admin.setRol(Rol.ADMINISTRADOR);
        admin.setActivo(true);
        admin.setCambioContrasenaPendiente(true);
        Usuario guardado = usuarios.save(admin);

        try {
            auditoria.registrar("CREAR", "USUARIOS", guardado.getUsuario(), guardado.getId(),
                    Map.of("motivo", "ADMINISTRADOR_INICIAL_BOOTSTRAP"), "127.0.0.1");
        } catch (Exception e) {
            log.warn("No se pudo auditar el administrador inicial: {}", e.getMessage());
        }
        log.info("Administrador inicial creado para el usuario '{}' (contrasena temporal pendiente "
                + "de cambio en el primer inicio de sesion)", guardado.getUsuario());
    }
}
