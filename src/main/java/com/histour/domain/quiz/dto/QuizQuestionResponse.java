package com.histour.domain.quiz.dto;

import java.util.List;

public record QuizQuestionResponse(
        Long sessionId,
        Long quizId,
        Long heritageId,
        String heritageName,
        String title,
        String content,
        String source,
        String difficulty,
        int sortOrder,
        List<QuizChoiceResponse> choices
) {
}
