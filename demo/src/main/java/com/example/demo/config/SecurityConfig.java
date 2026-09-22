package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)

                // NEW: without this, cross-origin requests from your frontend
                // (served on a different port = a different origin to the
                // browser) are blocked before your login credentials are even
                // checked -- this was the cause of "Failed to fetch".
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .authorizeHttpRequests(auth -> auth
                        // FIX: /api/ocr/** is no longer permitAll(). This is
                        // the endpoint that receives uploaded patient
                        // documents -- it must never be reachable without
                        // authentication.
                        //
                        // Only the login endpoint itself is whitelisted, since
                        // a user needs to be able to reach it before they have
                        // a token. Update this path to match your actual
                        // login controller's mapping.
                        .requestMatchers("/api/auth/login").permitAll()

                        // NEW: order confirm/reject restricted to a REVIEWER
                        // role rather than any authenticated user. Adjust the
                        // role name to whatever your staff role scheme uses.
                        .requestMatchers("/api/orders/*/confirm", "/api/orders/*/reject")
                        .hasRole("REVIEWER")

                        .anyRequest().authenticated()
                )

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // NEW: explicitly disable Spring's default login form and HTTP
                // Basic auth. Without this, Spring Boot's auto-configuration
                // generates a random throwaway user + password on every boot
                // ("Using generated security password: ...") since no
                // UserDetailsService bean is defined -- dead weight and noise
                // now that JWT is the only auth mechanism in use.
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)

                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Local dev origins for the static frontend. Add/replace with your
        // actual deployed frontend origin(s) once this moves beyond local
        // testing -- avoid a wildcard ("*") once real credentials are
        // involved; permissive CORS is a real exposure for an app handling
        // patient data.
        configuration.setAllowedOrigins(List.of(
                "http://localhost:8000",
                "http://localhost:5500",
                "http://127.0.0.1:8000",
                "http://127.0.0.1:5500"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}