package com.histour.domain.quiz;

import com.histour.client.QuizAiClient;
import com.histour.domain.quiz.dto.AiQuizGenerateRequest;
import com.histour.domain.quiz.dto.AiQuizQuestion;
import com.histour.domain.quiz.dto.QuizAnswerSubmitRequest;
import com.histour.domain.quiz.dto.QuizResultResponse;
import com.histour.domain.quiz.dto.QuizResultSubmitRequest;
import com.histour.domain.quiz.dto.QuizSessionCreateRequest;
import com.histour.domain.quiz.dto.QuizSessionResponse;
import com.histour.domain.quiz.entity.QuizResult;
import com.histour.domain.quiz.entity.Quiz;
import com.histour.domain.quiz.entity.QuizChoice;
import com.histour.domain.quiz.entity.QuizGradingRow;
import com.histour.domain.quiz.entity.QuizSession;
import com.histour.domain.quiz.entity.QuizSessionQuestion;
import com.histour.domain.trip.Trip;
import com.histour.domain.trip.TripMapper;
import com.histour.domain.trip.VisitLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private QuizMapper quizMapper;

    @Mock
    private TripMapper tripMapper;

    @Mock
    private QuizAiClient quizAiClient;

    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private QuizService quizService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void createSessionReturnsExistingSessionWithoutCreatingAgain() {
        Long tripId = 1L;
        when(tripMapper.findTripById(tripId)).thenReturn(trip(tripId, USER_ID));
        when(quizMapper.findSessionQuestionsByTripId(tripId)).thenReturn(List.of(
                sessionQuestion(10L, tripId, 100L, 1L, "서울 숭례문", 1)
        ));
        when(quizMapper.findChoicesByQuizIds(List.of(100L))).thenReturn(List.of(
                choice(1L, 100L, "선택지 A"),
                choice(2L, 100L, "선택지 B")
        ));

        QuizSessionResponse response = quizService.createSession(USER_ID, new QuizSessionCreateRequest(tripId));

        assertThat(response.tripId()).isEqualTo(tripId);
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().getFirst().sessionId()).isEqualTo(10L);
        assertThat(response.questions().getFirst().choices()).hasSize(2);

        verify(quizMapper, never()).insertSession(any());
        verify(tripMapper, never()).findVisitLogsByTripId(any());
    }

    @Test
    void createSessionCreatesSessionsFromVisitedHeritageQuizzes() {
        Long tripId = 1L;
        when(tripMapper.findTripById(tripId)).thenReturn(trip(tripId, USER_ID));
        when(quizMapper.findSessionQuestionsByTripId(tripId))
                .thenReturn(List.of())
                .thenReturn(List.of(
                        sessionQuestion(10L, tripId, 100L, 1L, "서울 숭례문", 1),
                        sessionQuestion(11L, tripId, 101L, 1L, "서울 숭례문", 2),
                        sessionQuestion(12L, tripId, 102L, 1L, "서울 숭례문", 3),
                        sessionQuestion(13L, tripId, 103L, 1L, "서울 숭례문", 4),
                        sessionQuestion(14L, tripId, 104L, 1L, "서울 숭례문", 5),
                        sessionQuestion(15L, tripId, 105L, 1L, "서울 숭례문", 6),
                        sessionQuestion(16L, tripId, 106L, 1L, "서울 숭례문", 7),
                        sessionQuestion(17L, tripId, 107L, 1L, "서울 숭례문", 8),
                        sessionQuestion(18L, tripId, 108L, 1L, "서울 숭례문", 9),
                        sessionQuestion(19L, tripId, 109L, 1L, "서울 숭례문", 10)
                ));
        when(tripMapper.findVisitLogsByTripId(tripId)).thenReturn(List.of(
                visitLog(tripId, 1L)
        ));
        when(quizMapper.findQuizzesByHeritageIds(List.of(1L))).thenReturn(List.of(
                quiz(100L, 1L),
                quiz(101L, 1L),
                quiz(102L, 1L),
                quiz(103L, 1L),
                quiz(104L, 1L),
                quiz(105L, 1L),
                quiz(106L, 1L),
                quiz(107L, 1L),
                quiz(108L, 1L),
                quiz(109L, 1L)
        ));
        when(quizMapper.findChoicesByQuizIds(List.of(100L, 101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L))).thenReturn(List.of(
                choice(1L, 100L, "선택지 A"),
                choice(2L, 101L, "선택지 B")
        ));

        QuizSessionResponse response = quizService.createSession(USER_ID, new QuizSessionCreateRequest(tripId));

        ArgumentCaptor<QuizSession> captor = ArgumentCaptor.forClass(QuizSession.class);
        verify(quizMapper, times(10)).insertSession(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(QuizSession::getTripId)
                .containsOnly(tripId);
        assertThat(captor.getAllValues())
                .extracting(QuizSession::getQuizId)
                .containsExactlyInAnyOrder(100L, 101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L);
        assertThat(captor.getAllValues())
                .extracting(QuizSession::getSortOrder)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(captor.getAllValues())
                .extracting(QuizSession::getStatus)
                .containsOnly("CREATED");

        assertThat(response.tripId()).isEqualTo(tripId);
        assertThat(response.totalCount()).isEqualTo(10);
        assertThat(response.questions())
                .extracting(question -> question.quizId())
                .containsExactly(100L, 101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L);
    }

    @Test
    void createSessionThrowsWhenTripDoesNotExist() {
        when(tripMapper.findTripById(404L)).thenReturn(null);

        assertThatThrownBy(() -> quizService.createSession(USER_ID, new QuizSessionCreateRequest(404L)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("여행을 찾을 수 없습니다.");
    }

    @Test
    void createSessionThrowsWhenTripBelongsToAnotherUser() {
        Long tripId = 1L;
        when(tripMapper.findTripById(tripId)).thenReturn(trip(tripId, 99L));

        assertThatThrownBy(() -> quizService.createSession(USER_ID, new QuizSessionCreateRequest(tripId)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("해당 여행에 접근할 수 없습니다.");

        verify(quizMapper, never()).findSessionQuestionsByTripId(any());
        verify(quizMapper, never()).insertSession(any());
    }

    @Test
    void createSessionThrowsWhenTripIsNotCompleted() {
        Long tripId = 1L;
        when(tripMapper.findTripById(tripId)).thenReturn(trip(tripId, USER_ID, "IN_PROGRESS"));

        assertThatThrownBy(() -> quizService.createSession(USER_ID, new QuizSessionCreateRequest(tripId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("완료된 여행에만 퀴즈를 생성할 수 있습니다.");

        verify(quizMapper, never()).findSessionQuestionsByTripId(any());
        verify(tripMapper, never()).findVisitLogsByTripId(any());
        verify(quizMapper, never()).insertSession(any());
    }

    @Test
    void createSessionThrowsWhenVisitLogsAreEmpty() {
        Long tripId = 1L;
        when(tripMapper.findTripById(tripId)).thenReturn(trip(tripId, USER_ID));
        when(quizMapper.findSessionQuestionsByTripId(tripId)).thenReturn(List.of());
        when(tripMapper.findVisitLogsByTripId(tripId)).thenReturn(List.of());

        assertThatThrownBy(() -> quizService.createSession(USER_ID, new QuizSessionCreateRequest(tripId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("방문 기록이 없어 퀴즈를 생성할 수 없습니다.");
    }

    @Test
    void createSessionGeneratesAiQuizzesWhenExistingQuizzesAreLessThanTen() {
        Long tripId = 1L;
        when(tripMapper.findTripById(tripId)).thenReturn(trip(tripId, USER_ID));
        when(quizMapper.findSessionQuestionsByTripId(tripId))
                .thenReturn(List.of())
                .thenReturn(List.of(
                        sessionQuestion(10L, tripId, 100L, 1L, "서울 숭례문", 1),
                        sessionQuestion(11L, tripId, 1000L, 1L, "서울 숭례문", 2),
                        sessionQuestion(12L, tripId, 1001L, 1L, "서울 숭례문", 3),
                        sessionQuestion(13L, tripId, 1002L, 1L, "서울 숭례문", 4),
                        sessionQuestion(14L, tripId, 1003L, 1L, "서울 숭례문", 5),
                        sessionQuestion(15L, tripId, 1004L, 1L, "서울 숭례문", 6),
                        sessionQuestion(16L, tripId, 1005L, 1L, "서울 숭례문", 7),
                        sessionQuestion(17L, tripId, 1006L, 1L, "서울 숭례문", 8),
                        sessionQuestion(18L, tripId, 1007L, 1L, "서울 숭례문", 9),
                        sessionQuestion(19L, tripId, 1008L, 1L, "서울 숭례문", 10)
                ));
        when(tripMapper.findVisitLogsByTripId(tripId)).thenReturn(List.of(
                visitLog(tripId, 1L)
        ));
        when(quizMapper.findQuizzesByHeritageIds(List.of(1L))).thenReturn(List.of(
                quiz(100L, 1L)
        ));
        when(quizAiClient.generateQuestions(any(AiQuizGenerateRequest.class))).thenReturn(List.of(
                aiQuestion(1L, 0),
                aiQuestion(1L, 1),
                aiQuestion(1L, 2),
                aiQuestion(1L, 3),
                aiQuestion(1L, 4),
                aiQuestion(1L, 5),
                aiQuestion(1L, 6),
                aiQuestion(1L, 7),
                aiQuestion(1L, 8)
        ));
        assignGeneratedQuizIds(1000L);
        when(quizMapper.findChoicesByQuizIds(any())).thenReturn(List.of());

        QuizSessionResponse response = quizService.createSession(USER_ID, new QuizSessionCreateRequest(tripId));

        ArgumentCaptor<AiQuizGenerateRequest> aiRequestCaptor = ArgumentCaptor.forClass(AiQuizGenerateRequest.class);
        verify(quizAiClient).generateQuestions(aiRequestCaptor.capture());
        assertThat(aiRequestCaptor.getValue().count()).isEqualTo(9);
        assertThat(aiRequestCaptor.getValue().visitedHeritages())
                .extracting(visited -> visited.heritageId())
                .containsExactly(1L);

        verify(quizMapper, times(9)).insertQuiz(any(Quiz.class));
        verify(quizMapper, times(36)).insertChoice(any(QuizChoice.class));
        verify(quizMapper, times(10)).insertSession(any(QuizSession.class));
        InOrder order = inOrder(quizAiClient, transactionManager, quizMapper);
        order.verify(quizAiClient).generateQuestions(any(AiQuizGenerateRequest.class));
        order.verify(transactionManager).getTransaction(any());
        order.verify(quizMapper).insertQuiz(any(Quiz.class));
        assertThat(response.totalCount()).isEqualTo(10);
    }

    @Test
    void createSessionSelectsQuizzesAcrossVisitedHeritages() {
        Long tripId = 1L;
        when(tripMapper.findTripById(tripId)).thenReturn(trip(tripId, USER_ID));
        when(quizMapper.findSessionQuestionsByTripId(tripId))
                .thenReturn(List.of())
                .thenReturn(List.of(
                        sessionQuestion(10L, tripId, 100L, 1L, "서울 숭례문", 1),
                        sessionQuestion(11L, tripId, 200L, 2L, "서울 원각사지 십층석탑", 2),
                        sessionQuestion(12L, tripId, 101L, 1L, "서울 숭례문", 3),
                        sessionQuestion(13L, tripId, 201L, 2L, "서울 원각사지 십층석탑", 4),
                        sessionQuestion(14L, tripId, 102L, 1L, "서울 숭례문", 5),
                        sessionQuestion(15L, tripId, 202L, 2L, "서울 원각사지 십층석탑", 6),
                        sessionQuestion(16L, tripId, 103L, 1L, "서울 숭례문", 7),
                        sessionQuestion(17L, tripId, 203L, 2L, "서울 원각사지 십층석탑", 8),
                        sessionQuestion(18L, tripId, 104L, 1L, "서울 숭례문", 9),
                        sessionQuestion(19L, tripId, 204L, 2L, "서울 원각사지 십층석탑", 10)
                ));
        when(tripMapper.findVisitLogsByTripId(tripId)).thenReturn(List.of(
                visitLog(tripId, 1L),
                visitLog(tripId, 2L)
        ));
        when(quizMapper.findQuizzesByHeritageIds(List.of(1L, 2L))).thenReturn(List.of(
                quiz(100L, 1L), quiz(101L, 1L), quiz(102L, 1L), quiz(103L, 1L), quiz(104L, 1L),
                quiz(105L, 1L), quiz(106L, 1L), quiz(107L, 1L), quiz(108L, 1L), quiz(109L, 1L),
                quiz(200L, 2L), quiz(201L, 2L), quiz(202L, 2L), quiz(203L, 2L), quiz(204L, 2L),
                quiz(205L, 2L), quiz(206L, 2L), quiz(207L, 2L), quiz(208L, 2L), quiz(209L, 2L)
        ));
        when(quizMapper.findChoicesByQuizIds(any())).thenReturn(List.of());

        quizService.createSession(USER_ID, new QuizSessionCreateRequest(tripId));

        ArgumentCaptor<QuizSession> captor = ArgumentCaptor.forClass(QuizSession.class);
        verify(quizMapper, times(10)).insertSession(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(QuizSession::getQuizId)
                .anyMatch(quizId -> quizId >= 100L && quizId < 200L)
                .anyMatch(quizId -> quizId >= 200L && quizId < 300L);
    }

    @Test
    void submitResultsGradesAndStoresAnswers() {
        Long tripId = 1L;
        when(quizMapper.findGradingRowsBySessionIds(List.of(10L, 11L))).thenReturn(List.of(
                gradingRow(10L, tripId, 100L, null, 1L),
                gradingRow(11L, tripId, 101L, null, 4L)
        ));
        when(quizMapper.findChoicesByIds(List.of(1L, 5L))).thenReturn(List.of(
                choice(1L, 100L, "정답", true),
                choice(5L, 101L, "오답", false)
        ));
        when(tripMapper.findTripById(tripId)).thenReturn(trip(tripId, USER_ID));

        QuizResultResponse response = quizService.submitResults(USER_ID, new QuizResultSubmitRequest(List.of(
                new QuizAnswerSubmitRequest(10L, 1L),
                new QuizAnswerSubmitRequest(11L, 5L)
        )));

        ArgumentCaptor<QuizResult> resultCaptor = ArgumentCaptor.forClass(QuizResult.class);
        verify(quizMapper, times(2)).insertResult(resultCaptor.capture());
        assertThat(resultCaptor.getAllValues())
                .extracting(QuizResult::getQuizSessionId, QuizResult::getSelectedChoiceId, QuizResult::isCorrect)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, 1L, true),
                        org.assertj.core.groups.Tuple.tuple(11L, 5L, false)
                );
        verify(quizMapper).updateSessionStatus(10L, "SUBMITTED");
        verify(quizMapper).updateSessionStatus(11L, "SUBMITTED");

        assertThat(response.tripId()).isEqualTo(tripId);
        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.correctCount()).isEqualTo(1);
        assertThat(response.accuracy()).isEqualTo(50);
        assertThat(response.results())
                .extracting(result -> result.correctChoiceId())
                .containsExactly(1L, 4L);
    }

    @Test
    void submitResultsUsesBatchQueriesForTenAnswers() {
        Long tripId = 1L;
        List<Long> sessionIds = List.of(10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L);
        List<Long> choiceIds = List.of(1000L, 1001L, 1002L, 1003L, 1004L, 1005L, 1006L, 1007L, 1008L, 1009L);
        when(quizMapper.findGradingRowsBySessionIds(sessionIds)).thenReturn(List.of(
                gradingRow(10L, tripId, 100L, null, 1000L),
                gradingRow(11L, tripId, 101L, null, 1001L),
                gradingRow(12L, tripId, 102L, null, 1002L),
                gradingRow(13L, tripId, 103L, null, 1003L),
                gradingRow(14L, tripId, 104L, null, 1004L),
                gradingRow(15L, tripId, 105L, null, 1005L),
                gradingRow(16L, tripId, 106L, null, 1006L),
                gradingRow(17L, tripId, 107L, null, 1007L),
                gradingRow(18L, tripId, 108L, null, 1008L),
                gradingRow(19L, tripId, 109L, null, 1009L)
        ));
        when(quizMapper.findChoicesByIds(choiceIds)).thenReturn(List.of(
                choice(1000L, 100L, "선택지 1", true),
                choice(1001L, 101L, "선택지 2", true),
                choice(1002L, 102L, "선택지 3", true),
                choice(1003L, 103L, "선택지 4", true),
                choice(1004L, 104L, "선택지 5", true),
                choice(1005L, 105L, "선택지 6", true),
                choice(1006L, 106L, "선택지 7", true),
                choice(1007L, 107L, "선택지 8", true),
                choice(1008L, 108L, "선택지 9", true),
                choice(1009L, 109L, "선택지 10", true)
        ));
        when(tripMapper.findTripById(tripId)).thenReturn(trip(tripId, USER_ID));

        QuizResultResponse response = quizService.submitResults(USER_ID, new QuizResultSubmitRequest(List.of(
                new QuizAnswerSubmitRequest(10L, 1000L),
                new QuizAnswerSubmitRequest(11L, 1001L),
                new QuizAnswerSubmitRequest(12L, 1002L),
                new QuizAnswerSubmitRequest(13L, 1003L),
                new QuizAnswerSubmitRequest(14L, 1004L),
                new QuizAnswerSubmitRequest(15L, 1005L),
                new QuizAnswerSubmitRequest(16L, 1006L),
                new QuizAnswerSubmitRequest(17L, 1007L),
                new QuizAnswerSubmitRequest(18L, 1008L),
                new QuizAnswerSubmitRequest(19L, 1009L)
        )));

        assertThat(response.totalCount()).isEqualTo(10);
        assertThat(response.correctCount()).isEqualTo(10);
        verify(quizMapper, times(1)).findGradingRowsBySessionIds(sessionIds);
        verify(quizMapper, times(1)).findChoicesByIds(choiceIds);
        verify(tripMapper, times(1)).findTripById(tripId);
        verify(quizMapper, times(10)).insertResult(any(QuizResult.class));
        verify(quizMapper, times(10)).updateSessionStatus(any(), eq("SUBMITTED"));
        verify(quizMapper, never()).findSessionQuestionBySessionId(any());
        verify(quizMapper, never()).findResultBySessionId(any());
        verify(quizMapper, never()).findChoiceById(any());
        verify(quizMapper, never()).findCorrectChoiceByQuizId(any());
    }

    @Test
    void submitResultsThrowsWhenChoiceDoesNotBelongToQuiz() {
        Long tripId = 1L;
        when(quizMapper.findGradingRowsBySessionIds(List.of(10L))).thenReturn(List.of(
                gradingRow(10L, tripId, 100L, null, 4L)
        ));
        when(quizMapper.findChoicesByIds(List.of(1L))).thenReturn(List.of(
                choice(1L, 999L, "다른 문제 선택지", false)
        ));
        when(tripMapper.findTripById(tripId)).thenReturn(trip(tripId, USER_ID));

        assertThatThrownBy(() -> quizService.submitResults(USER_ID, new QuizResultSubmitRequest(List.of(
                new QuizAnswerSubmitRequest(10L, 1L)
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("퀴즈에 속하지 않은 선택지입니다.");

        verify(quizMapper, never()).insertResult(any());
        verify(quizMapper, never()).updateSessionStatus(any(), any());
    }

    private QuizSessionQuestion sessionQuestion(Long sessionId,
                                                Long tripId,
                                                Long quizId,
                                                Long heritageId,
                                                String heritageName,
                                                int sortOrder) {
        return QuizSessionQuestion.builder()
                .sessionId(sessionId)
                .tripId(tripId)
                .quizId(quizId)
                .heritageId(heritageId)
                .heritageName(heritageName)
                .title("퀴즈 제목")
                .content("퀴즈 본문")
                .explanation("퀴즈 해설")
                .source("AI_GENERATED")
                .difficulty("MEDIUM")
                .sortOrder(sortOrder)
                .build();
    }

    private QuizGradingRow gradingRow(Long sessionId,
                                      Long tripId,
                                      Long quizId,
                                      Long existingResultId,
                                      Long correctChoiceId) {
        return QuizGradingRow.builder()
                .sessionId(sessionId)
                .tripId(tripId)
                .quizId(quizId)
                .explanation("퀴즈 해설")
                .existingResultId(existingResultId)
                .correctChoiceId(correctChoiceId)
                .build();
    }

    private Trip trip(Long tripId, Long userId) {
        return trip(tripId, userId, "COMPLETED");
    }

    private Trip trip(Long tripId, Long userId, String status) {
        return Trip.builder()
                .id(tripId)
                .userId(userId)
                .status(status)
                .build();
    }

    private QuizChoice choice(Long id, Long quizId, String content) {
        return choice(id, quizId, content, false);
    }

    private QuizChoice choice(Long id, Long quizId, String content, boolean correct) {
        return QuizChoice.builder()
                .id(id)
                .quizId(quizId)
                .content(content)
                .correct(correct)
                .build();
    }

    private VisitLog visitLog(Long tripId, Long heritageId) {
        return VisitLog.builder()
                .tripId(tripId)
                .heritageId(heritageId)
                .build();
    }

    private Quiz quiz(Long id, Long heritageId) {
        return Quiz.builder()
                .id(id)
                .heritageId(heritageId)
                .build();
    }

    private AiQuizQuestion aiQuestion(Long heritageId, int index) {
        return new AiQuizQuestion(
                heritageId,
                "AI 퀴즈 " + index,
                "AI 생성 문제 " + index,
                List.of("정답", "오답1", "오답2", "오답3"),
                0,
                "AI 생성 해설 " + index,
                "MEDIUM"
        );
    }

    private void assignGeneratedQuizIds(long startId) {
        final long[] nextId = {startId};
        doAnswer(invocation -> {
            Quiz quiz = invocation.getArgument(0);
            quiz.setId(nextId[0]++);
            return null;
        }).when(quizMapper).insertQuiz(any(Quiz.class));
    }
}
