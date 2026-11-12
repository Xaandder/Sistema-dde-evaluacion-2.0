package com.semillero.evaluacion.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.auth.dto.LoginRespuesta;
import com.semillero.evaluacion.exception.CredencialesInvalidasException;
import com.semillero.evaluacion.exception.CuentaInactivaException;
import com.semillero.evaluacion.exception.ReglaNegocioException;
import com.semillero.evaluacion.seguridad.JwtService;
import com.semillero.evaluacion.usuarios.Rol;
import com.semillero.evaluacion.usuarios.Usuario;
import com.semillero.evaluacion.usuarios.UsuarioRepositorio;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    private static final String SECRETO = "SECRETO-DE-PRUEBAS-CON-MAS-DE-32-CARACTERES";

    @Mock
    private UsuarioRepositorio usuarios;
    @Mock
    private AuditoriaServicio auditoria;

    private AuthService servicio;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // Construido en BeforeEach para que los @Mock ya esten inyectados.
        servicio = new AuthService(usuarios, auditoria, encoder,
                new JwtService(SECRETO, 60, new JsonMapper()));
    }

    private Usuario usuario(Rol rol, boolean activo, boolean pendiente) {
        Usuario u = new Usuario();
        u.setUsuario("profesor1");
        u.setContrasenaHash(encoder.encode("ClaveActual123"));
        u.setRol(rol);
        u.setActivo(activo);
        u.setCambioContrasenaPendiente(pendiente);
        ReflectionTestUtils.setField(u, "id", 1L);
        return u;
    }

    @Test
    @DisplayName("Login con credenciales correctas devuelve token, rol y bandera de cambio")
    void loginCorrecto() {
        Usuario cuenta = usuario(Rol.PROFESOR, true, true);
        when(usuarios.findByUsuario("profesor1")).thenReturn(Optional.of(cuenta));

        LoginRespuesta respuesta = servicio.login("profesor1", "ClaveActual123", "1.2.3.4");

        assertTrue(respuesta.token() != null && !respuesta.token().isBlank());
        assertEquals("PROFESOR", respuesta.rol());
        assertTrue(respuesta.cambioContrasenaPendiente());
        verify(auditoria).registrar(eq("LOGIN"), eq("USUARIOS"), eq("profesor1"),
                eq(cuenta.getId()), any(), eq("1.2.3.4"));
    }

    @Test
    @DisplayName("Login con contrasena incorrecta lanza 401 y audita LOGIN_FALLIDO")
    void loginContrasenaIncorrecta() {
        when(usuarios.findByUsuario("profesor1")).thenReturn(Optional.of(usuario(Rol.PROFESOR, true, false)));

        assertThrows(CredencialesInvalidasException.class,
                () -> servicio.login("profesor1", "Incorrecta999", "1.2.3.4"));

        verify(auditoria).registrar(eq("LOGIN_FALLIDO"), eq("USUARIOS"), eq("profesor1"),
                anyLong(), eq(Map.of("motivo", "CONTRASENA_INCORRECTA")), eq("1.2.3.4"));
    }

    @Test
    @DisplayName("Login de usuario inexistente audita USUARIO_DESCONOCIDO sin vincular ID")
    void loginUsuarioInexistente() {
        when(usuarios.findByUsuario("fantasma")).thenReturn(Optional.empty());

        assertThrows(CredencialesInvalidasException.class,
                () -> servicio.login("fantasma", "LoQueSea123", "1.2.3.4"));

        verify(auditoria).registrar(eq("LOGIN_FALLIDO"), eq("USUARIOS"), eq("USUARIO_DESCONOCIDO"),
                isNull(), any(), eq("1.2.3.4"));
    }

    @Test
    @DisplayName("Login de cuenta inactiva lanza 403 y audita el motivo")
    void loginCuentaInactiva() {
        when(usuarios.findByUsuario("profesor1")).thenReturn(Optional.of(usuario(Rol.PROFESOR, false, false)));

        assertThrows(CuentaInactivaException.class,
                () -> servicio.login("profesor1", "ClaveActual123", "1.2.3.4"));

        verify(auditoria).registrar(eq("LOGIN_FALLIDO"), eq("USUARIOS"), eq("profesor1"),
                anyLong(), eq(Map.of("motivo", "CUENTA_INACTIVA")), eq("1.2.3.4"));
    }

    @Test
    @DisplayName("Cambio de contrasena correcto: nuevo hash, bandera apagada y auditoria")
    void cambioDeContrasenaCorrecto() {
        Usuario cuenta = usuario(Rol.PROFESOR, true, true);
        when(usuarios.findById(1L)).thenReturn(Optional.of(cuenta));

        LoginRespuesta respuesta = servicio.cambiarContrasena(
                1L, "ClaveActual123", "NuevaClave456", "NuevaClave456", "1.2.3.4");

        assertFalse(respuesta.cambioContrasenaPendiente());
        assertNotEquals(cuenta.getContrasenaHash(), encoder.encode("ClaveActual123"));
        // El objeto guardado tiene la bandera apagada.
        assertFalse(cuenta.getCambioContrasenaPendiente());
        assertTrue(encoder.matches("NuevaClave456", cuenta.getContrasenaHash()));
        verify(usuarios).save(cuenta);
        verify(auditoria).registrar(eq("ACTUALIZAR"), eq("USUARIOS"), eq("profesor1"),
                eq(1L), any(), eq("1.2.3.4"));
    }

    @Test
    @DisplayName("Cambio de contrasena con actual incorrecta se rechaza y se audita")
    void cambioConActualIncorrecta() {
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario(Rol.PROFESOR, true, true)));

        assertThrows(CredencialesInvalidasException.class,
                () -> servicio.cambiarContrasena(1L, "MalaClave000", "NuevaClave456",
                        "NuevaClave456", "1.2.3.4"));

        verify(auditoria).registrar(eq("ACTUALIZAR_FALLIDO"), eq("USUARIOS"), anyString(),
                anyLong(), any(), eq("1.2.3.4"));
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("Politica: la nueva contrasena debe combinar letras y numeros")
    void cambioConPoliticaDebil() {
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario(Rol.PROFESOR, true, true)));

        assertThrows(ReglaNegocioException.class,
                () -> servicio.cambiarContrasena(1L, "ClaveActual123", "1234567890",
                        "1234567890", "1.2.3.4"));

        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("Politica: la confirmacion debe coincidir con la nueva contrasena")
    void cambioConConfirmacionDistinta() {
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario(Rol.PROFESOR, true, true)));

        assertThrows(ReglaNegocioException.class,
                () -> servicio.cambiarContrasena(1L, "ClaveActual123", "NuevaClave456",
                        "OtraClave999", "1.2.3.4"));

        verify(usuarios, never()).save(any());
    }
}
