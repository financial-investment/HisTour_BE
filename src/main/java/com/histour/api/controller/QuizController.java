package com.histour.api.controller;

import com.histour.common.response.ApiResponse;
import com.histour.domain.quiz.QuizService;
import com.histour.domain.quiz.dto.QuizResultResponse;
import com.histour.domain.quiz.dto.QuizResultSubmitRequest;
import com.histour.domain.quiz.dto.QuizSessionCreateRequest;
import com.histour.domain.quiz.dto.QuizSessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Quiz", description = "여행 후 퀴즈 API")
@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @Operation(
            summary = "여행 기반 퀴즈 세션 생성",
            description = "완료된 여행의 tripId를 받아 방문 기록 기반 퀴즈 10개를 생성하거나 기존 세션을 반환합니다. " +
                    "기존 문제가 부족하면 AI로 문제를 생성해 저장합니다."
    )
    @PostMapping("/sessions")
    public ApiResponse<QuizSessionResponse> createSession(
            Authentication authentication,
            @RequestBody @Valid QuizSessionCreateRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(quizService.createSession(userId, request));
    }

    @Operation(
            summary = "퀴즈 세션 조회",
            description = "tripId에 대해 이미 생성된 퀴즈 세션의 문제와 객관식 선택지를 반환합니다. 정답 정보는 포함하지 않습니다."
    )
    @GetMapping("/sessions")
    public ApiResponse<QuizSessionResponse> getSession(
            Authentication authentication,
            @RequestParam Long tripId
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(quizService.getSessionByTripId(userId, tripId));
    }

    @Operation(
            summary = "퀴즈 답안 제출",
            description = "각 문제의 sessionId와 사용자가 선택한 choiceId를 제출하면 채점 후 결과를 저장하고 정답률과 해설을 반환합니다."
    )
    @PostMapping("/results")
    public ApiResponse<QuizResultResponse> submitResults(
            Authentication authentication,
            @RequestBody @Valid QuizResultSubmitRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(quizService.submitResults(userId, request));
    }

    @Operation(
            summary = "퀴즈 결과 조회",
            description = "tripId에 대해 저장된 퀴즈 채점 결과를 반환합니다. 사용자가 선택한 선택지, 정답 선택지, 정오답, 해설을 포함합니다."
    )
    @GetMapping("/results")
    public ApiResponse<QuizResultResponse> getResults(
            Authentication authentication,
            @RequestParam Long tripId
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(quizService.getResultsByTripId(userId, tripId));
    }
}
