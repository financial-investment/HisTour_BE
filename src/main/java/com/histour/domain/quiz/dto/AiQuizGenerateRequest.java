package com.histour.domain.quiz.dto;

import java.util.List;

public record AiQuizGenerateRequest(
        int count,
        List<AiVisitedHeritage> visitedHeritages
) {
}
