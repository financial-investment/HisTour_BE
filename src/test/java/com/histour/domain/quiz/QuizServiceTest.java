package com.histour.domain.quiz;

import com.histour.domain.quiz.dto.QuizSessionCreateRequest;
import com.histour.domain.quiz.dto.QuizSessionResponse;
import com.histour.domain.quiz.entity.Quiz;
import com.histour.domain.quiz.entity.QuizChoice;
import com.histour.domain.quiz.entity.QuizSession;
import com.histour.domain.quiz.entity.QuizSessionQuestion;
import com.histour.domain.trip.Trip;
import com.histour.domain.trip.TripMapper;
import com.histour.domain.trip.VisitLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock
    private QuizMapper quizMapper;

    @Mock
    private TripMapper tripMapper;

    @InjectMocks
    private QuizService quizService;

    @Test
    void createSessionReturnsExistingSessionWithoutCreatingAgain() {
        Long tripId = 1L;
        when(tripMapper.findTripById(tripId)).thenReturn(Trip.builder().id(tripId).build());
        when(quizMapper.findSessionQuestionsByTripId(tripId)).thenReturn(List.of(
                sessionQuestion(10L, tripId, 100L, 1L, "서울 숭례문", 1)
        ));
        when(quizMapper.findChoicesByQuizIds(List.of(100L))).thenReturn(List.of(
                choice(1L, 100L, "선택지 A"),
                choice(2L, 100L, "선택지 B")
        ));

        QuizSessionResponse response = quizService.createSession(new QuizSessionCreateRequest(tripId));

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
        when(tripMapper.findTripById(tripId)).thenReturn(Trip.builder().id(tripId).build());
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

        QuizSessionResponse response = quizService.createSession(new QuizSessionCreateRequest(tripId));

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

        assertThatThrownBy(() -> quizService.createSession(new QuizSessionCreateRequest(404L)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("여행을 찾을 수 없습니다.");
    }

    @Test
    void createSessionThrowsWhenVisitLogsAreEmpty() {
        Long tripId = 1L;
        when(tripMapper.findTripById(tripId)).thenReturn(Trip.builder().id(tripId).build());
        when(quizMapper.findSessionQuestionsByTripId(tripId)).thenReturn(List.of());
        when(tripMapper.findVisitLogsByTripId(tripId)).thenReturn(List.of());

        assertThatThrownBy(() -> quizService.createSession(new QuizSessionCreateRequest(tripId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("방문 기록이 없어 퀴즈를 생성할 수 없습니다.");
    }

    @Test
    void createSessionThrowsWhenExistingQuizzesAreLessThanTen() {
        Long tripId = 1L;
        when(tripMapper.findTripById(tripId)).thenReturn(Trip.builder().id(tripId).build());
        when(quizMapper.findSessionQuestionsByTripId(tripId)).thenReturn(List.of());
        when(tripMapper.findVisitLogsByTripId(tripId)).thenReturn(List.of(
                visitLog(tripId, 1L)
        ));
        when(quizMapper.findQuizzesByHeritageIds(List.of(1L))).thenReturn(List.of(
                quiz(100L, 1L)
        ));

        assertThatThrownBy(() -> quizService.createSession(new QuizSessionCreateRequest(tripId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("퀴즈 10개를 만들기 위한 기존 문제가 부족합니다. AI 생성이 필요합니다.");

        verify(quizMapper, never()).insertSession(any());
    }

    @Test
    void createSessionSelectsQuizzesAcrossVisitedHeritages() {
        Long tripId = 1L;
        when(tripMapper.findTripById(tripId)).thenReturn(Trip.builder().id(tripId).build());
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

        quizService.createSession(new QuizSessionCreateRequest(tripId));

        ArgumentCaptor<QuizSession> captor = ArgumentCaptor.forClass(QuizSession.class);
        verify(quizMapper, times(10)).insertSession(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(QuizSession::getQuizId)
                .anyMatch(quizId -> quizId >= 100L && quizId < 200L)
                .anyMatch(quizId -> quizId >= 200L && quizId < 300L);
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
                .source("AI_GENERATED")
                .difficulty("MEDIUM")
                .sortOrder(sortOrder)
                .build();
    }

    private QuizChoice choice(Long id, Long quizId, String content) {
        return QuizChoice.builder()
                .id(id)
                .quizId(quizId)
                .content(content)
                .correct(false)
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
}
