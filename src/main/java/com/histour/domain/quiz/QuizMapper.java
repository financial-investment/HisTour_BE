package com.histour.domain.quiz;

import com.histour.domain.quiz.entity.Quiz;
import com.histour.domain.quiz.entity.QuizChoice;
import com.histour.domain.quiz.entity.QuizSession;
import com.histour.domain.quiz.entity.QuizSessionQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuizMapper {
    List<Quiz> findQuizzesByHeritageIds(@Param("heritageIds") List<Long> heritageIds);

    void insertSession(QuizSession session);

    List<QuizSessionQuestion> findSessionQuestionsByTripId(@Param("tripId") Long tripId);

    List<QuizChoice> findChoicesByQuizIds(@Param("quizIds") List<Long> quizIds);
}
