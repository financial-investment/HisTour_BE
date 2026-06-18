package com.histour.api.controller;

import com.histour.common.response.ApiResponse;
import com.histour.domain.quiz.QuizService;
import com.histour.domain.quiz.dto.QuizSessionCreateRequest;
import com.histour.domain.quiz.dto.QuizSessionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @PostMapping("/sessions")
    public ApiResponse<QuizSessionResponse> createSession(@RequestBody @Valid QuizSessionCreateRequest request) {
        return ApiResponse.ok(quizService.createSession(request));
    }

    @GetMapping("/sessions")
    public ApiResponse<QuizSessionResponse> getSession(@RequestParam Long tripId) {
        return ApiResponse.ok(quizService.getSessionByTripId(tripId));
    }
}
