package com.histour.domain.quiz.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizChoice {
    private Long id;
    private Long quizId;
    private String content;
    private boolean correct;
}
