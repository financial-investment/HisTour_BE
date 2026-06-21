package com.histour.domain.quiz;

import com.histour.domain.quiz.entity.Quiz;
import com.histour.domain.quiz.entity.QuizChoice;
import com.histour.domain.quiz.entity.QuizResult;
import com.histour.domain.quiz.entity.QuizSession;
import com.histour.domain.quiz.entity.QuizSessionQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuizMapper {
    List<Quiz> findQuizzesByHeritageIds(@Param("heritageIds") List<Long> heritageIds);

    void insertQuiz(Quiz quiz);

    void insertChoice(QuizChoice choice);

    void insertSession(QuizSession session);

    List<QuizSessionQuestion> findSessionQuestionsByTripId(@Param("tripId") Long tripId);

    QuizSessionQuestion findSessionQuestionBySessionId(@Param("sessionId") Long sessionId);

    List<QuizChoice> findChoicesByQuizIds(@Param("quizIds") List<Long> quizIds);

    QuizChoice findChoiceById(@Param("choiceId") Long choiceId);

    QuizChoice findCorrectChoiceByQuizId(@Param("quizId") Long quizId);

    QuizResult findResultBySessionId(@Param("sessionId") Long sessionId);

    void insertResult(QuizResult result);

    void updateSessionStatus(@Param("sessionId") Long sessionId, @Param("status") String status);
}
