package com.histour.domain.quiz.dto;

import java.util.List;

public record AiQuizQuestion(
        Long heritageId,
        String title,
        String content,
        List<String> choices,
        int answerIndex,
        String explanation,
        String difficulty
) {
}
