package com.histour.domain.quiz;

import com.histour.domain.quiz.dto.QuizChoiceResponse;
import com.histour.domain.quiz.dto.QuizQuestionResponse;
import com.histour.domain.quiz.dto.QuizSessionCreateRequest;
import com.histour.domain.quiz.dto.QuizSessionResponse;
import com.histour.domain.quiz.entity.Quiz;
import com.histour.domain.quiz.entity.QuizChoice;
import com.histour.domain.quiz.entity.QuizSession;
import com.histour.domain.quiz.entity.QuizSessionQuestion;
import com.histour.domain.trip.Trip;
import com.histour.domain.trip.TripMapper;
import com.histour.domain.trip.VisitLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizService {

    private static final int DAILY_QUIZ_COUNT = 10;

    private final QuizMapper quizMapper;
    private final TripMapper tripMapper;

    @Transactional
    public QuizSessionResponse createSession(QuizSessionCreateRequest request) {
        Trip trip = tripMapper.findTripById(request.tripId());
        if (trip == null) {
            throw new NoSuchElementException("여행을 찾을 수 없습니다.");
        }

        List<QuizSessionQuestion> existingQuestions = quizMapper.findSessionQuestionsByTripId(request.tripId());
        if (!existingQuestions.isEmpty()) {
            return toResponse(request.tripId(), existingQuestions);
        }

        List<VisitLog> visitLogs = tripMapper.findVisitLogsByTripId(request.tripId());
        if (visitLogs.isEmpty()) {
            throw new IllegalStateException("방문 기록이 없어 퀴즈를 생성할 수 없습니다.");
        }

        List<Long> heritageIds = visitLogs.stream()
                .map(VisitLog::getHeritageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (heritageIds.isEmpty()) {
            throw new IllegalStateException("방문 유적지 정보가 없어 퀴즈를 생성할 수 없습니다.");
        }

        List<Quiz> candidateQuizzes = quizMapper.findQuizzesByHeritageIds(heritageIds);
        if (candidateQuizzes.isEmpty()) {
            throw new IllegalStateException("방문 유적지와 연결된 퀴즈가 없습니다.");
        }
        List<Quiz> quizzes = selectBalancedRandomQuizzes(candidateQuizzes, heritageIds, DAILY_QUIZ_COUNT);
        if (quizzes.size() < DAILY_QUIZ_COUNT) {
            throw new IllegalStateException("퀴즈 10개를 만들기 위한 기존 문제가 부족합니다. AI 생성이 필요합니다.");
        }

        for (int i = 0; i < quizzes.size(); i++) {
            quizMapper.insertSession(QuizSession.builder()
                    .tripId(request.tripId())
                    .quizId(quizzes.get(i).getId())
                    .sortOrder(i + 1)
                    .status("CREATED")
                    .build());
        }

        return getSessionByTripId(request.tripId());
    }

    public QuizSessionResponse getSessionByTripId(Long tripId) {
        List<QuizSessionQuestion> questions = quizMapper.findSessionQuestionsByTripId(tripId);
        if (questions.isEmpty()) {
            throw new NoSuchElementException("생성된 퀴즈 세션이 없습니다.");
        }
        return toResponse(tripId, questions);
    }

    private QuizSessionResponse toResponse(Long tripId, List<QuizSessionQuestion> questions) {
        List<Long> quizIds = questions.stream()
                .map(QuizSessionQuestion::getQuizId)
                .toList();

        Map<Long, List<QuizChoice>> choicesByQuizId = quizIds.isEmpty()
                ? Map.of()
                : quizMapper.findChoicesByQuizIds(quizIds).stream()
                        .collect(Collectors.groupingBy(
                                QuizChoice::getQuizId,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        List<QuizQuestionResponse> questionResponses = questions.stream()
                .map(question -> new QuizQuestionResponse(
                        question.getSessionId(),
                        question.getQuizId(),
                        question.getHeritageId(),
                        question.getHeritageName(),
                        question.getTitle(),
                        question.getContent(),
                        question.getSource(),
                        question.getDifficulty(),
                        question.getSortOrder(),
                        choicesByQuizId.getOrDefault(question.getQuizId(), List.of()).stream()
                                .map(choice -> new QuizChoiceResponse(choice.getId(), choice.getContent()))
                                .toList()
                ))
                .toList();

        return new QuizSessionResponse(tripId, questionResponses.size(), questionResponses);
    }

    private List<Quiz> selectBalancedRandomQuizzes(List<Quiz> candidates, List<Long> heritageIds, int targetCount) {
        Map<Long, List<Quiz>> quizzesByHeritageId = candidates.stream()
                .collect(Collectors.groupingBy(
                        Quiz::getHeritageId,
                        LinkedHashMap::new,
                        Collectors.toCollection(ArrayList::new)
                ));

        quizzesByHeritageId.values().forEach(Collections::shuffle);

        List<Quiz> selected = new ArrayList<>();
        Set<Long> selectedQuizIds = new HashSet<>();
        while (selected.size() < targetCount) {
            boolean addedInRound = false;
            for (Long heritageId : heritageIds) {
                List<Quiz> quizzes = quizzesByHeritageId.getOrDefault(heritageId, List.of());
                while (!quizzes.isEmpty()) {
                    Quiz quiz = quizzes.removeFirst();
                    if (selectedQuizIds.add(quiz.getId())) {
                        selected.add(quiz);
                        addedInRound = true;
                        break;
                    }
                }
                if (selected.size() == targetCount) {
                    break;
                }
            }
            if (!addedInRound) {
                break;
            }
        }

        return selected;
    }
}
