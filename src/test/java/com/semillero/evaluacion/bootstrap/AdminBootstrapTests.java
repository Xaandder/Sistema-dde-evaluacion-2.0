package com.semillero.evaluacion.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.semillero.evaluacion.auditoria.AuditoriaServicio;
import com.semillero.evaluacion.usuarios.Rol;
import com.semillero.evaluacion.usuarios.Usuario;
import com.semillero.evaluacion.usuarios.UsuarioRepositorio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTests {

    @Mock
    private UsuarioRepositorio usuarios;
    @Mock
    private AuditoriaServicio auditoria;
    @Captor
    private ArgumentCaptor<Usuario> captorUsuario;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private AdminBootstrap bootstrap(String usuario, String contrasena) {
        return new AdminBootstrap(usuarios, encoder, auditoria, usuario, contrasena);
    }

    @Test
    @DisplayName("Si ya existen administradores, no crea ni sobrescribe nada")
    void noCreaSiYaExistenAdministradores() {
        when(usuarios.countByRol(Rol.ADMINISTRADOR)).thenReturn(2L);

        bootstrap("nuevoAdmin", "Temporal123").crearAdministradorInicial();

        verify(usuarios, never()).save(any());
        verify(auditoria, never()).registrar(any(), any(), any(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("Sin credenciales por variables de entorno, no crea administrador")
    void noCreaSinVariablesDeEntorno() {
        when(usuarios.countByRol(Rol.ADMINISTRADOR)).thenReturn(0L);

        bootstrap("", "").crearAdministradorInicial();

        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("Crea el primer ADMINISTRADOR con contrasena temporal y lo audita")
    void creaElPrimerAdministrador() {
        when(usuarios.countByRol(Rol.ADMINISTRADOR)).thenReturn(0L);
        when(usuarios.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        bootstrap("AdminUnico", "Temporal123").crearAdministradorInicial();

        verify(usuarios).save(captorUsuario.capture());
        Usuario guardado = captorUsuario.getValue();
        assertEquals("AdminUnico", guardado.getUsuario());
        assertEquals(Rol.ADMINISTRADOR, guardado.getRol());
        assertTrue(guardado.getActivo());
        // Contraseña temporal: cambio obligatorio en el primer login.
        assertTrue(guardado.getCambioContrasenaPendiente());
        assertFalse(guardado.getContrasenaHash().equals("Temporal123"));
        assertTrue(encoder.matches("Temporal123", guardado.getContrasenaHash()));
        verify(auditoria).registrar(eq("CREAR"), eq("USUARIOS"), eq("AdminUnico"),
                any(), any(), eq("127.0.0.1"));
    }
}
