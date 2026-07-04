package com.digitalocean.llmproxy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;

/**
 * Optional API-key authentication for public endpoints; health remains open when configured.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties securityProperties)
            throws Exception {
        if (!securityProperties.enabled()) {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }

        RequestHeaderAuthenticationFilter filter = new RequestHeaderAuthenticationFilter();
        filter.setPrincipalRequestHeader(API_KEY_HEADER);
        filter.setExceptionIfHeaderMissing(false);
        filter.setAuthenticationManager(authentication -> {
            String apiKey = (String) authentication.getPrincipal();
            if (apiKey != null && !apiKey.isBlank() && apiKey.equals(securityProperties.apiKey())) {
                authentication.setAuthenticated(true);
                return new PreAuthenticatedAuthenticationToken(apiKey, null);
            }
            authentication.setAuthenticated(false);
            return authentication;
        });

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilter(filter)
                .authorizeHttpRequests(auth -> {
                    if (securityProperties.permitHealth()) {
                        auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    }
                    auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/api-docs", "/api-docs/**")
                            .permitAll();
                    auth.requestMatchers("/generate", "/metrics", "/actuator/prometheus").authenticated();
                    auth.anyRequest().permitAll();
                })
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"title\":\"Unauthorized\",\"detail\":\"Valid X-API-Key required\"}");
                }));

        return http.build();
    }
}
