package com.semillero.evaluacion.seguridad;

import com.semillero.evaluacion.usuarios.UsuarioRepositorio;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Configuracion de seguridad (Etapa 3).
 *
 * <p>API stateless con JWT: sin sesiones, CSRF deshabilitado (no hay cookies
 * de sesion que proteger). Reglas vigentes: login, Swagger/OpenAPI y /error
 * son publicos; el resto de /api/** exige token; /actuator/** solo para
 * ADMINISTRADOR (decision 3); y cualquier otra ruta exige autenticacion por
 * defecto (correccion A-1: nada queda abierto sin decidirlo explicitamente).</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtService jwtService(
            @Value("${app.jwt.secreto}") String secreto,
            @Value("${app.jwt.ttl-minutos}") long ttlMinutos,
            ObjectMapper json) {
        return new JwtService(secreto, ttlMinutos, json);
    }

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtService jwtService,
            ObjectProvider<UsuarioRepositorio> repositorio,
            ObjectMapper json) {
        return new JwtAuthenticationFilter(jwtService, repositorio, json);
    }

    @Bean
    SecurityFilterChain cadenaFiltros(HttpSecurity http, JwtAuthenticationFilter filtro,
            ObjectMapper json) throws Exception {
        RespuestaJsonSeguridad respuestas = new RespuestaJsonSeguridad(json);

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(acceso -> acceso
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/error")
                        .permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/**").authenticated()
                        // Correccion MEDIO A-1: por defecto TODO lo demas exige
                        // autenticacion (antes: permitAll como catch-all, que
                        // dejaria abiertos endpoints futuros fuera de /api/**).
                        .anyRequest().authenticated())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas))
                .addFilterBefore(filtro, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
