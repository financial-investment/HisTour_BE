package com.histour.api.controller;

import com.histour.common.response.ApiResponse;
import com.histour.domain.quiz.QuizService;
import com.histour.domain.quiz.dto.QuizResultResponse;
import com.histour.domain.quiz.dto.QuizResultSubmitRequest;
import com.histour.domain.quiz.dto.QuizSessionCreateRequest;
import com.histour.domain.quiz.dto.QuizSessionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @PostMapping("/sessions")
    public ApiResponse<QuizSessionResponse> createSession(
            Authentication authentication,
            @RequestBody @Valid QuizSessionCreateRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(quizService.createSession(userId, request));
    }

    @GetMapping("/sessions")
    public ApiResponse<QuizSessionResponse> getSession(
            Authentication authentication,
            @RequestParam Long tripId
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(quizService.getSessionByTripId(userId, tripId));
    }

    @PostMapping("/results")
    public ApiResponse<QuizResultResponse> submitResults(
            Authentication authentication,
            @RequestBody @Valid QuizResultSubmitRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(quizService.submitResults(userId, request));
    }
}
