package com.histour.domain.auth.dto;

public record LoginRequest(
        String email,
        String password
) {
}
