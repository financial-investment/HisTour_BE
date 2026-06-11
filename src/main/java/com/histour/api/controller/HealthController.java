package com.histour.api.controller;

import com.histour.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/")
    public ApiResponse<String> health() {
        return ApiResponse.ok("ok");
    }
}
