package com.semillero.evaluacion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SistemaEvaluacionApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    /** La aplicacion debe iniciar con todos sus componentes sin errores. */
    @Test
    @DisplayName("El contexto de la aplicacion se levanta correctamente")
    void contextLoads() {
    }

    /** Verifica que toda ruta desconocida devuelva el formato uniforme ApiError con 404. */
    @Test
    @DisplayName("Una ruta inexistente devuelve 404 con el formato uniforme ApiError")
    void rutaInexistenteDevuelveFormatoApiError() throws Exception {
        mockMvc.perform(get("/ruta-inexistente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.estado").value(404))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.mensaje").exists())
                .andExpect(jsonPath("$.ruta").value("/ruta-inexistente"));
    }
}
