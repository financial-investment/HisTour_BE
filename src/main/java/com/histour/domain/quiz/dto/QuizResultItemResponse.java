package com.histour.domain.quiz.dto;

public record QuizResultItemResponse(
        Long sessionId,
        Long quizId,
        boolean correct,
        Long selectedChoiceId,
        Long correctChoiceId,
        String explanation
) {
}
