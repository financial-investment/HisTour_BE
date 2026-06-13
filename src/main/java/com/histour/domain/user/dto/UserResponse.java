package com.histour.domain.user.dto;

import com.histour.domain.user.entity.User;

public record UserResponse(
        Long id,
        String email,
        String nickname,
        String preferredLang
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getPreferredLang()
        );
    }

}
