package com.histour.domain.quiz;

import com.histour.domain.quiz.entity.Quiz;
import com.histour.domain.quiz.entity.QuizChoice;
import com.histour.domain.quiz.entity.QuizGradingRow;
import com.histour.domain.quiz.entity.QuizResult;
import com.histour.domain.quiz.entity.QuizSession;
import com.histour.domain.quiz.entity.QuizSessionQuestion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/histour?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul",
        "spring.datasource.username=root",
        "spring.datasource.password=${DB_PASSWORD:}"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class QuizMapperTest {

    private static final long USER_ID = 990001L;
    private static final long TRIP_ID = 990001L;
    private static final long HERITAGE_ID = 990001L;

    @Autowired
    private QuizMapper quizMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbcTemplate.update("""
                INSERT INTO users (id, email, password, nickname)
                VALUES (?, ?, ?, ?)
                """, USER_ID, "quiz-mapper-test@example.com", "password", "quiz-mapper-test");
        jdbcTemplate.update("""
                INSERT INTO trips (id, user_id, title, trip_date, status)
                VALUES (?, ?, ?, CURDATE(), 'COMPLETED')
                """, TRIP_ID, USER_ID, "quiz mapper test trip");
        jdbcTemplate.update("""
                INSERT INTO heritage (
                    id, name, category, period, location,
                    ccba_kdcd, ccba_asno, ccba_ctcd
                )
                VALUES (
                    ?, ?, ?, ?,
                    ST_GeomFromText('POINT(37.5665 126.9780)', 4326),
                    ?, ?, ?
                )
                """, HERITAGE_ID, "테스트 유적지", "사적", "JOSEON",
                "QT", "990001", "99");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void quizSessionAndResultMapperQueriesMatchDdl() {
        Quiz quiz = Quiz.builder()
                .heritageId(HERITAGE_ID)
                .title("테스트 퀴즈")
                .content("테스트 문제 본문")
                .correctAnswer("정답 선택지")
                .explanation("테스트 해설")
                .source("AI_GENERATED")
                .difficulty("MEDIUM")
                .build();
        quizMapper.insertQuiz(quiz);

        QuizChoice correctChoice = QuizChoice.builder()
                .quizId(quiz.getId())
                .content("정답 선택지")
                .correct(true)
                .build();
        QuizChoice wrongChoice = QuizChoice.builder()
                .quizId(quiz.getId())
                .content("오답 선택지")
                .correct(false)
                .build();
        quizMapper.insertChoice(correctChoice);
        quizMapper.insertChoice(wrongChoice);

        List<Quiz> quizzes = quizMapper.findQuizzesByHeritageIds(List.of(HERITAGE_ID));
        assertThat(quizzes).hasSize(1);
        assertThat(quizzes.getFirst().getId()).isEqualTo(quiz.getId());
        assertThat(quizzes.getFirst().getExplanation()).isEqualTo("테스트 해설");
        assertThat(quizzes.getFirst().getSource()).isEqualTo("AI_GENERATED");

        QuizSession session = QuizSession.builder()
                .tripId(TRIP_ID)
                .quizId(quiz.getId())
                .sortOrder(1)
                .status("CREATED")
                .build();
        quizMapper.insertSession(session);

        List<QuizSessionQuestion> sessionQuestions = quizMapper.findSessionQuestionsByTripId(TRIP_ID);
        assertThat(sessionQuestions).hasSize(1);
        assertThat(sessionQuestions.getFirst().getSessionId()).isEqualTo(session.getId());
        assertThat(sessionQuestions.getFirst().getExplanation()).isEqualTo("테스트 해설");

        QuizSessionQuestion sessionQuestion = quizMapper.findSessionQuestionBySessionId(session.getId());
        assertThat(sessionQuestion.getQuizId()).isEqualTo(quiz.getId());
        assertThat(sessionQuestion.getHeritageName()).isEqualTo("테스트 유적지");

        List<QuizChoice> choices = quizMapper.findChoicesByQuizIds(List.of(quiz.getId()));
        assertThat(choices).hasSize(2);
        assertThat(quizMapper.findChoicesByIds(List.of(correctChoice.getId(), wrongChoice.getId())))
                .extracting(QuizChoice::getId)
                .containsExactlyInAnyOrder(correctChoice.getId(), wrongChoice.getId());
        assertThat(quizMapper.findChoiceById(correctChoice.getId()).isCorrect()).isTrue();
        assertThat(quizMapper.findCorrectChoiceByQuizId(quiz.getId()).getId()).isEqualTo(correctChoice.getId());

        List<QuizGradingRow> gradingRowsBeforeSubmit = quizMapper.findGradingRowsBySessionIds(List.of(session.getId()));
        assertThat(gradingRowsBeforeSubmit).hasSize(1);
        assertThat(gradingRowsBeforeSubmit.getFirst().getSessionId()).isEqualTo(session.getId());
        assertThat(gradingRowsBeforeSubmit.getFirst().getQuizId()).isEqualTo(quiz.getId());
        assertThat(gradingRowsBeforeSubmit.getFirst().getCorrectChoiceId()).isEqualTo(correctChoice.getId());
        assertThat(gradingRowsBeforeSubmit.getFirst().getExistingResultId()).isNull();

        QuizResult result = QuizResult.builder()
                .quizSessionId(session.getId())
                .selectedChoiceId(correctChoice.getId())
                .correct(true)
                .build();
        quizMapper.insertResult(result);

        QuizResult savedResult = quizMapper.findResultBySessionId(session.getId());
        assertThat(savedResult.getId()).isEqualTo(result.getId());
        assertThat(savedResult.getSelectedChoiceId()).isEqualTo(correctChoice.getId());
        assertThat(savedResult.isCorrect()).isTrue();

        List<QuizGradingRow> gradingRowsAfterSubmit = quizMapper.findGradingRowsBySessionIds(List.of(session.getId()));
        assertThat(gradingRowsAfterSubmit.getFirst().getExistingResultId()).isEqualTo(result.getId());

        quizMapper.updateSessionStatus(session.getId(), "SUBMITTED");
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM quiz_sessions WHERE id = ?",
                String.class,
                session.getId()
        );
        assertThat(status).isEqualTo("SUBMITTED");
    }

    private void cleanUp() {
        jdbcTemplate.update("""
                DELETE qr FROM quiz_results qr
                JOIN quiz_sessions qs ON qr.quiz_session_id = qs.id
                WHERE qs.trip_id = ?
                """, TRIP_ID);
        jdbcTemplate.update("DELETE FROM quiz_sessions WHERE trip_id = ?", TRIP_ID);
        jdbcTemplate.update("""
                DELETE qc FROM quiz_choices qc
                JOIN quiz q ON qc.quiz_id = q.id
                WHERE q.heritage_id = ?
                """, HERITAGE_ID);
        jdbcTemplate.update("DELETE FROM quiz WHERE heritage_id = ?", HERITAGE_ID);
        jdbcTemplate.update("DELETE FROM trips WHERE id = ?", TRIP_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM heritage WHERE id = ?", HERITAGE_ID);
    }
}
