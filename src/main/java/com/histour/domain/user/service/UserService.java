package com.histour.domain.user.service;

import com.histour.domain.user.dto.User;
import com.histour.domain.user.dto.UserRegistRequest;
import com.histour.domain.user.dto.UserResponse;
import com.histour.domain.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signUp(UserRegistRequest request) {
        if (userMapper.existsByEmail(request.email()) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        if (userMapper.existsByNickname(request.nickname()) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        User user = User.builder()
                .nickname(request.nickname())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .preferredLang(resolvePreferredLang(request.preferredLang()))
                .build();

        userMapper.save(user);
    }

    public UserResponse getUser(Long userId) {
        User user = userMapper.findById(userId);

        if (user == null) {
            throw new NoSuchElementException("사용자를 찾을 수 없습니다.");
        }

        return UserResponse.from(user);
    }

    private String resolvePreferredLang(String preferredLang) {
        if (preferredLang == null || preferredLang.isBlank()) {
            return "ko";
        }

        return preferredLang;
    }
}
