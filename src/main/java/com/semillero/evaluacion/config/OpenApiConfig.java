package com.semillero.evaluacion.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de la documentacion de la API con OpenAPI (Swagger UI).
 *
 * <p>Swagger UI sera la herramienta para documentar y probar los endpoints.
 * No es una interfaz de usuario final.</p>
 *
 * <p>En la Etapa 3 (seguridad) se agregara aqui el esquema de
 * autenticacion Bearer (JWT) para poder probar los endpoints protegidos.</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI sistemaEvaluacionOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sistema de Gestion de Calificaciones Academicas")
                        .description(
                                "Backend REST del sistema academico. Roles: ADMINISTRADOR, "
                                        + "PROFESOR, ESTUDIANTE y ACUDIENTE. "
                                        + "Etapa 1: esqueleto tecnico (sin base de datos ni seguridad).")
                        .version("v1")
                        .contact(new Contact().name("Semillero")));
    }
}
