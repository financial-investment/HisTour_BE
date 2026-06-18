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
public class Quiz {
    private Long id;
    private Long heritageId;
    private String heritageName;
    private String title;
    private String content;
    private String correctAnswer;
    private String explanation;
    private String source;
    private String difficulty;
    private LocalDateTime createdAt;
}
