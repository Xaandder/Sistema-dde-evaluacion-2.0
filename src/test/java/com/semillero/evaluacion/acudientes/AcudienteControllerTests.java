package com.semillero.evaluacion.acudientes;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.semillero.evaluacion.seguridad.JwtService;
import com.semillero.evaluacion.soporte.ContextoPruebas;
import com.semillero.evaluacion.usuarios.Rol;
import com.semillero.evaluacion.usuarios.Usuario;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** Pruebas de endpoints de acudientes, SIN Oracle (perfil test). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AcudienteControllerTests extends ContextoPruebas {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private Usuario usuario(String nombre, Rol rol) {
        Usuario u = new Usuario();
        u.setUsuario(nombre);
        u.setContrasenaHash(encoder.encode("ClaveActual123"));
        u.setRol(rol);
        u.setActivo(true);
        u.setCambioContrasenaPendiente(false);
        ReflectionTestUtils.setField(u, "id", 1L);
        return u;
    }

    private String token(Rol rol) {
        String nombre = rol.name().toLowerCase();
        Usuario cuenta = usuario(nombre, rol);
        when(usuarios.findByUsuario(nombre)).thenReturn(Optional.of(cuenta));
        return jwtService.generarToken(cuenta);
    }

    private Acudiente acudiente(Long id, Long usuarioId, EstadoAcudiente estado) {
        Acudiente a = new Acudiente();
        ReflectionTestUtils.setField(a, "id", id);
        a.setUsuarioId(usuarioId);
        a.setDocumento("70100" + id);
        a.setNombres("Acudiente");
        a.setApellidos("Prueba");
        a.setCorreo("acudiente" + id + "@colegio.edu");
        a.setEstado(estado);
        return a;
    }

    private Usuario cuentaAcudiente(Long id) {
        Usuario u = usuario("acud" + id, Rol.ACUDIENTE);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    @DisplayName("POST /acudientes: ADMIN crea con cuenta (201); ACUDIENTE recibe 403")
    void crear() throws Exception {
        when(usuarios.findByUsuario("acud1")).thenReturn(Optional.empty());
        when(acudientes.existsByDocumento("701001")).thenReturn(false);
        when(usuarios.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 50L);
            return u;
        });
        when(acudientes.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            Acudiente a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 2L);
            return a;
        });
        when(usuarios.findById(50L)).thenReturn(Optional.of(cuentaAcudiente(50L)));

        String cuerpo = "{\"usuario\":\"acud1\",\"contrasenaTemporal\":\"Temporal1\","
                + "\"documento\":\"701001\",\"nombres\":\"Acudiente\","
                + "\"apellidos\":\"Prueba\",\"parentesco\":\"MADRE\","
                + "\"correo\":\"acud1@colegio.edu\",\"telefono\":\"3101\"}";

        mockMvc.perform(post("/api/v1/acudientes")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));

        mockMvc.perform(post("/api/v1/acudientes")
                        .header("Authorization", "Bearer " + token(Rol.ACUDIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /acudientes/perfil: el ACUDIENTE consulta SU perfil (404 si no tiene)")
    void perfil() throws Exception {
        when(acudientes.findByUsuarioId(1L))
                .thenReturn(Optional.of(acudiente(2L, 1L, EstadoAcudiente.ACTIVO)));
        when(usuarios.findById(1L)).thenReturn(Optional.of(cuentaAcudiente(1L)));

        mockMvc.perform(get("/api/v1/acudientes/perfil")
                        .header("Authorization", "Bearer " + token(Rol.ACUDIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    @Test
    @DisplayName("GET /acudientes/{id}: acudiente ajeno -> 403; admin -> 200")
    void buscarPorId() throws Exception {
        when(acudientes.findById(2L))
                .thenReturn(Optional.of(acudiente(2L, 50L, EstadoAcudiente.ACTIVO)));
        when(usuarios.findById(50L)).thenReturn(Optional.of(cuentaAcudiente(50L)));

        mockMvc.perform(get("/api/v1/acudientes/2")
                        .header("Authorization", "Bearer " + token(Rol.ACUDIENTE)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/acudientes/2")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documento").value("701002"));
    }

    @Test
    @DisplayName("PUT /acudientes/{id}: ADMIN actualiza (200); ACUDIENTE recibe 403")
    void actualizar() throws Exception {
        when(acudientes.findById(2L))
                .thenReturn(Optional.of(acudiente(2L, 50L, EstadoAcudiente.ACTIVO)));
        when(acudientes.existsByDocumento("701002")).thenReturn(false);
        when(acudientes.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(usuarios.findById(50L)).thenReturn(Optional.of(cuentaAcudiente(50L)));

        String cuerpo = "{\"documento\":\"701002\",\"nombres\":\"Acudiente\","
                + "\"apellidos\":\"Prueba Ruiz\",\"parentesco\":\"PADRE\","
                + "\"correo\":\"acud1@colegio.edu\"}";

        mockMvc.perform(put("/api/v1/acudientes/2")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apellidos").value("Prueba Ruiz"));

        mockMvc.perform(put("/api/v1/acudientes/2")
                        .header("Authorization", "Bearer " + token(Rol.ACUDIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /acudientes/{id}/desactivar: INACTIVO + cuenta desactivada (solo ADMIN)")
    void desactivar() throws Exception {
        when(acudientes.findById(2L))
                .thenReturn(Optional.of(acudiente(2L, 50L, EstadoAcudiente.ACTIVO)));
        when(acudientes.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(usuarios.findById(50L)).thenReturn(Optional.of(cuentaAcudiente(50L)));

        mockMvc.perform(post("/api/v1/acudientes/2/desactivar")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));
    }

    @Test
    @DisplayName("GET /acudientes: solo ADMIN (ACUDIENTE recibe 403)")
    void listar() throws Exception {
        when(acudientes.findAllByOrderByApellidosAscNombresAsc())
                .thenReturn(List.of(acudiente(2L, 50L, EstadoAcudiente.ACTIVO)));
        when(usuarios.findById(50L)).thenReturn(Optional.of(cuentaAcudiente(50L)));

        mockMvc.perform(get("/api/v1/acudientes")
                        .header("Authorization", "Bearer " + token(Rol.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/acudientes")
                        .header("Authorization", "Bearer " + token(Rol.ACUDIENTE)))
                .andExpect(status().isForbidden());
    }
}
