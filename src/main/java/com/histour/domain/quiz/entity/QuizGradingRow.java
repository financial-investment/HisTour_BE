package com.histour.domain.quiz.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizGradingRow {
    private Long sessionId;
    private Long tripId;
    private Long quizId;
    private String explanation;
    private Long existingResultId;
    private Long correctChoiceId;
}
