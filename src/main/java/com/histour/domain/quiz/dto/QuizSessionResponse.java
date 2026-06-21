package com.histour.domain.quiz.dto;

import java.util.List;

public record QuizSessionResponse(
        Long tripId,
        int totalCount,
        List<QuizQuestionResponse> questions
) {
}
