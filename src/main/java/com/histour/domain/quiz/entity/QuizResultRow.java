package com.histour.domain.quiz.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizResultRow {
    private Long sessionId;
    private Long tripId;
    private Long quizId;
    private boolean correct;
    private Long selectedChoiceId;
    private Long correctChoiceId;
    private String explanation;
}
