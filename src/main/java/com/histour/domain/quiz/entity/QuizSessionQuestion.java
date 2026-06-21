package com.histour.domain.quiz.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizSessionQuestion {
    private Long sessionId;
    private Long tripId;
    private Long quizId;
    private Long heritageId;
    private String heritageName;
    private String title;
    private String content;
    private String explanation;
    private String source;
    private String difficulty;
    private int sortOrder;
}
