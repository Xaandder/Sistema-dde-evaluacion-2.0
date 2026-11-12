package com.semillero.evaluacion.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de la documentacion de la API con OpenAPI (Swagger UI).
 *
 * <p>Swagger UI sera la herramienta para documentar y probar los endpoints.
 * No es una interfaz de usuario final.</p>
 *
 * <p>Etapa 3: esquema de seguridad Bearer (JWT) para probar los endpoints
 * protegidos desde la interfaz de Swagger ("Authorize" con el token).</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_BEARER = "JWT";

    @Bean
    OpenAPI sistemaEvaluacionOpenAPI() {
        SecurityRequirement requisitoBearer = new SecurityRequirement().addList(ESQUEMA_BEARER);
        return new OpenAPI()
                .info(new Info()
                        .title("Sistema de Gestion de Calificaciones Academicas")
                        .description(
                                "Backend REST del sistema academico. Roles: ADMINISTRADOR, "
                                        + "PROFESOR, ESTUDIANTE y ACUDIENTE. "
                                        + "Etapa 3: autenticacion JWT, cambio obligatorio de "
                                        + "contrasena en el primer inicio de sesion.")
                        .version("v1")
                        .contact(new Contact().name("Semillero")))
                .components(new Components()
                        .addSecuritySchemes(ESQUEMA_BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(requisitoBearer);
    }
}
