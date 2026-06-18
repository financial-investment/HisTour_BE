package com.histour.domain.quiz.dto;

import java.util.List;

public record QuizResultResponse(
        Long tripId,
        int totalCount,
        int correctCount,
        int accuracy,
        List<QuizResultItemResponse> results
) {
}
