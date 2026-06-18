package com.histour.domain.quiz.dto;

import jakarta.validation.constraints.NotNull;

public record QuizAnswerSubmitRequest(
        @NotNull(message = "sessionId는 필수입니다.")
        Long sessionId,
        @NotNull(message = "choiceId는 필수입니다.")
        Long choiceId
) {
}
