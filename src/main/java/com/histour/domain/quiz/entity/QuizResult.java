package com.histour.domain.quiz.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizResult {
    private Long id;
    private Long quizSessionId;
    private Long selectedChoiceId;
    private boolean correct;
    private LocalDateTime answeredAt;
}
