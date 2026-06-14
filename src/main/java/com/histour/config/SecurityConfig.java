package com.histour.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.histour.domain.auth.jwt.JwtAuthenticationFilter;
import com.histour.domain.auth.jwt.JwtProvider;
import com.histour.domain.auth.jwt.JwtVerificationFilter;
import com.histour.domain.auth.security.CustomAccessDeniedHandler;
import com.histour.domain.auth.security.CustomAuthenticationEntryPoint;
import com.histour.domain.auth.service.RefreshTokenService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtVerificationFilter jwtVerificationFilter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            AuthenticationManager authenticationManager,
            JwtProvider jwtProvider,
            RefreshTokenService refreshTokenService,
            ObjectMapper objectMapper,
            Validator validator
    ) {
        return new JwtAuthenticationFilter(
                authenticationManager,
                jwtProvider,
                refreshTokenService,
                objectMapper,
                validator
        );
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception
                    .authenticationEntryPoint(customAuthenticationEntryPoint)
                    .accessDeniedHandler(customAccessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/login").permitAll()
                    .requestMatchers("/api/auth/refresh").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/user").permitAll()
                    .anyRequest().authenticated()
            )
            .addFilterAt(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtVerificationFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtVerificationFilter> jwtVerificationFilterRegistration() {
        FilterRegistrationBean<JwtVerificationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(jwtVerificationFilter);
        registration.setEnabled(false);
        return registration;
    }
}
