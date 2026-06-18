package com.histour.domain.quiz.dto;

import jakarta.validation.constraints.NotNull;

public record QuizSessionCreateRequest(
        @NotNull(message = "tripId는 필수입니다.")
        Long tripId
) {
}
