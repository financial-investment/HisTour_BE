package com.histour.api.controller;

import com.histour.common.response.ApiResponse;
import com.histour.domain.user.dto.UserRegistRequest;
import com.histour.domain.user.dto.UserResponse;
import com.histour.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;


    @PostMapping()
    public ResponseEntity<ApiResponse<Void>> signUp(@Valid @RequestBody UserRegistRequest request){
        userService.signUp(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(null));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        userService.getUser(userId)
                )
        );
    }

}
