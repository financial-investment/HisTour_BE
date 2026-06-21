package com.histour.api.controller;

import com.histour.common.response.ApiResponse;
import com.histour.domain.user.dto.UserRegistRequest;
import com.histour.domain.user.dto.UserResponse;
import com.histour.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "회원 API")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 닉네임을 받아 새 사용자를 등록합니다.")
    @PostMapping()
    public ResponseEntity<ApiResponse<Void>> signUp(@Valid @RequestBody UserRegistRequest request){
        userService.signUp(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(null));
    }

    @Operation(summary = "내 정보 조회", description = "JWT 인증 정보를 기준으로 현재 로그인한 사용자의 정보를 반환합니다.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyInfo(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.ok(userService.getUser(userId)));
    }

}
