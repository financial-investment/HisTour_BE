package com.histour.domain.user.dto;


public record UserRegistRequest (
    String nickname,
    String email,
    String password
){}