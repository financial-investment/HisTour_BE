package com.histour.domain.auth.service;

import com.histour.domain.auth.security.AuthenticatedUser;
import com.histour.domain.user.dto.User;
import com.histour.domain.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final AuthService authService = new AuthService(userMapper);

    @Test
    void loadUserByUsernameReturnsAuthenticatedUser() {
        // Spring Security calls UserDetailsService.loadUserByUsername() during login.
        // In this project the username value is the user's email, because
        // JwtAuthenticationFilter creates UsernamePasswordAuthenticationToken(email, password).
        User user = User.builder()
                .id(1L)
                .email("user@email.com")
                .password("encoded-password")
                .build();
        when(userMapper.findByEmail("user@email.com")).thenReturn(user);

        UserDetails userDetails = authService.loadUserByUsername("user@email.com");

        // AuthenticatedUser wraps the DB user so successfulAuthentication() can read userId
        // and JwtProvider can create an access token with that id as the subject.
        assertThat(userDetails).isInstanceOf(AuthenticatedUser.class);
        assertThat(((AuthenticatedUser) userDetails).getId()).isEqualTo(1L);

        // Spring Security's DaoAuthenticationProvider compares the raw login password
        // against this encoded password through PasswordEncoder.matches().
        assertThat(userDetails.getUsername()).isEqualTo("user@email.com");
        assertThat(userDetails.getPassword()).isEqualTo("encoded-password");
    }

    @Test
    void loadUserByUsernameThrowsWhenUserDoesNotExist() {
        // Missing users must be reported as UsernameNotFoundException so Spring Security
        // treats the login attempt as an authentication failure, not as a server error.
        when(userMapper.findByEmail("missing@email.com")).thenReturn(null);

        assertThatThrownBy(() -> authService.loadUserByUsername("missing@email.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
