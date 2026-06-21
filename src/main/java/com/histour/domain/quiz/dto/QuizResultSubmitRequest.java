package com.histour.domain.quiz.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record QuizResultSubmitRequest(
        @NotEmpty(message = "답안은 최소 1개 이상 제출해야 합니다.")
        List<@Valid QuizAnswerSubmitRequest> answers
) {
}
