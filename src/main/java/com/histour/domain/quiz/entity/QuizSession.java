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
public class QuizSession {
    private Long id;
    private Long tripId;
    private Long quizId;
    private int sortOrder;
    private String status;
    private LocalDateTime createdAt;
}
