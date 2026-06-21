package com.histour.domain.quiz;

import com.histour.client.QuizAiClient;
import com.histour.domain.quiz.dto.AiQuizGenerateRequest;
import com.histour.domain.quiz.dto.AiQuizQuestion;
import com.histour.domain.quiz.dto.AiVisitedHeritage;
import com.histour.domain.quiz.dto.QuizChoiceResponse;
import com.histour.domain.quiz.dto.QuizQuestionResponse;
import com.histour.domain.quiz.dto.QuizResultItemResponse;
import com.histour.domain.quiz.dto.QuizResultResponse;
import com.histour.domain.quiz.dto.QuizResultSubmitRequest;
import com.histour.domain.quiz.dto.QuizSessionCreateRequest;
import com.histour.domain.quiz.dto.QuizSessionResponse;
import com.histour.domain.quiz.entity.Quiz;
import com.histour.domain.quiz.entity.QuizChoice;
import com.histour.domain.quiz.entity.QuizResult;
import com.histour.domain.quiz.entity.QuizSession;
import com.histour.domain.quiz.entity.QuizSessionQuestion;
import com.histour.domain.trip.Trip;
import com.histour.domain.trip.TripMapper;
import com.histour.domain.trip.VisitLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final QuizAiClient quizAiClient;
    private final PlatformTransactionManager transactionManager;

    public QuizSessionResponse createSession(Long userId, QuizSessionCreateRequest request) {
        Trip trip = validateTripOwnerAndGetTrip(userId, request.tripId());

        if (!"COMPLETED".equals(trip.getStatus())) {
            throw new IllegalStateException("완료된 여행에만 퀴즈를 생성할 수 있습니다.");
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
        List<Quiz> quizzes = selectBalancedRandomQuizzes(candidateQuizzes, heritageIds, DAILY_QUIZ_COUNT);
        List<AiQuizQuestion> aiQuestions = List.of();
        if (quizzes.size() < DAILY_QUIZ_COUNT) {
            int missingCount = DAILY_QUIZ_COUNT - quizzes.size();
            aiQuestions = generateValidAiQuestions(missingCount, visitLogs, heritageIds);
        }
        if (quizzes.size() + aiQuestions.size() < DAILY_QUIZ_COUNT) {
            throw new IllegalStateException("퀴즈 10개를 생성하지 못했습니다.");
        }

        List<Quiz> existingQuizzes = quizzes;
        List<AiQuizQuestion> generatedAiQuestions = aiQuestions;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<Quiz> sessionQuizzes = new ArrayList<>(existingQuizzes);
            sessionQuizzes.addAll(saveAiQuizzes(generatedAiQuestions));

            for (int i = 0; i < sessionQuizzes.size(); i++) {
                quizMapper.insertSession(QuizSession.builder()
                        .tripId(request.tripId())
                        .quizId(sessionQuizzes.get(i).getId())
                        .sortOrder(i + 1)
                        .status("CREATED")
                        .build());
            }
        });

        return getSessionByTripId(userId, request.tripId());
    }

    public QuizSessionResponse getSessionByTripId(Long userId, Long tripId) {
        validateTripOwner(tripId, userId);

        List<QuizSessionQuestion> questions = quizMapper.findSessionQuestionsByTripId(tripId);
        if (questions.isEmpty()) {
            throw new NoSuchElementException("생성된 퀴즈 세션이 없습니다.");
        }
        return toResponse(tripId, questions);
    }

    public QuizResultResponse submitResults(Long userId, QuizResultSubmitRequest request) {
        List<PreparedQuizResult> preparedResults = prepareQuizResults(userId, request);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (PreparedQuizResult prepared : preparedResults) {
                quizMapper.insertResult(QuizResult.builder()
                        .quizSessionId(prepared.item().sessionId())
                        .selectedChoiceId(prepared.item().selectedChoiceId())
                        .correct(prepared.item().correct())
                        .build());
                quizMapper.updateSessionStatus(prepared.item().sessionId(), "SUBMITTED");
            }
        });

        int correctCount = (int) preparedResults.stream()
                .filter(prepared -> prepared.item().correct())
                .count();
        int totalCount = preparedResults.size();
        int accuracy = totalCount == 0 ? 0 : (int) Math.round(correctCount * 100.0 / totalCount);
        Long tripId = preparedResults.getFirst().tripId();

        return new QuizResultResponse(
                tripId,
                totalCount,
                correctCount,
                accuracy,
                preparedResults.stream()
                        .map(PreparedQuizResult::item)
                        .toList()
        );
    }

    private List<PreparedQuizResult> prepareQuizResults(Long userId, QuizResultSubmitRequest request) {
        List<PreparedQuizResult> preparedResults = new ArrayList<>();
        Long targetTripId = null;

        for (var answer : request.answers()) {
            QuizSessionQuestion sessionQuestion = quizMapper.findSessionQuestionBySessionId(answer.sessionId());
            if (sessionQuestion == null) {
                throw new NoSuchElementException("퀴즈 세션을 찾을 수 없습니다.");
            }
            if (targetTripId == null) {
                targetTripId = sessionQuestion.getTripId();
                validateTripOwner(targetTripId, userId);
            } else if (!Objects.equals(targetTripId, sessionQuestion.getTripId())) {
                throw new IllegalArgumentException("하나의 여행에 속한 답안만 제출할 수 있습니다.");
            }

            if (quizMapper.findResultBySessionId(answer.sessionId()) != null) {
                throw new IllegalStateException("이미 제출된 퀴즈입니다.");
            }

            QuizChoice selectedChoice = quizMapper.findChoiceById(answer.choiceId());
            if (selectedChoice == null || !Objects.equals(selectedChoice.getQuizId(), sessionQuestion.getQuizId())) {
                throw new IllegalArgumentException("퀴즈에 속하지 않은 선택지입니다.");
            }

            QuizChoice correctChoice = quizMapper.findCorrectChoiceByQuizId(sessionQuestion.getQuizId());
            if (correctChoice == null) {
                throw new IllegalStateException("정답 선택지가 없습니다.");
            }

            preparedResults.add(new PreparedQuizResult(
                    sessionQuestion.getTripId(),
                    new QuizResultItemResponse(
                            answer.sessionId(),
                            sessionQuestion.getQuizId(),
                            selectedChoice.isCorrect(),
                            answer.choiceId(),
                            correctChoice.getId(),
                            sessionQuestion.getExplanation()
                    )
            ));
        }

        return preparedResults;
    }

    private record PreparedQuizResult(
            Long tripId,
            QuizResultItemResponse item
    ) {
    }

    private void validateTripOwner(Long tripId, Long userId) {
        Trip trip = tripMapper.findTripById(tripId);
        if (trip == null) {
            throw new NoSuchElementException("여행을 찾을 수 없습니다.");
        }
        if (!Objects.equals(trip.getUserId(), userId)) {
            throw new AccessDeniedException("해당 여행에 접근할 수 없습니다.");
        }
    }

    private Trip validateTripOwnerAndGetTrip(Long userId, Long tripId) {
        Trip trip = tripMapper.findTripById(tripId);
        if (trip == null) {
            throw new NoSuchElementException("여행을 찾을 수 없습니다.");
        }
        if (!Objects.equals(trip.getUserId(), userId)) {
            throw new AccessDeniedException("해당 여행에 접근할 수 없습니다.");
        }
        return trip;
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

    private List<AiQuizQuestion> generateValidAiQuestions(int missingCount, List<VisitLog> visitLogs, List<Long> heritageIds) {
        Set<Long> validHeritageIds = new HashSet<>(heritageIds);
        AiQuizGenerateRequest request = new AiQuizGenerateRequest(
                missingCount,
                visitLogs.stream()
                        .filter(visitLog -> visitLog.getHeritageId() != null)
                        .map(visitLog -> new AiVisitedHeritage(
                                visitLog.getHeritageId(),
                                visitLog.getHeritageName(),
                                visitLog.getExplanation()
                        ))
                        .toList()
        );

        List<AiQuizQuestion> generatedQuestions = quizAiClient.generateQuestions(request);
        List<AiQuizQuestion> validQuestions = new ArrayList<>();
        for (AiQuizQuestion question : generatedQuestions) {
            if (validQuestions.size() == missingCount) {
                break;
            }
            if (!isValidAiQuestion(question, validHeritageIds)) {
                continue;
            }
            validQuestions.add(question);
        }
        return validQuestions;
    }

    private List<Quiz> saveAiQuizzes(List<AiQuizQuestion> questions) {
        List<Quiz> savedQuizzes = new ArrayList<>();
        for (AiQuizQuestion question : questions) {
            String correctAnswer = question.choices().get(question.answerIndex());
            Quiz quiz = Quiz.builder()
                    .heritageId(question.heritageId())
                    .title(question.title())
                    .content(question.content())
                    .correctAnswer(correctAnswer)
                    .explanation(question.explanation())
                    .source("AI_GENERATED")
                    .difficulty(normalizeDifficulty(question.difficulty()))
                    .build();
            quizMapper.insertQuiz(quiz);

            for (int i = 0; i < question.choices().size(); i++) {
                quizMapper.insertChoice(QuizChoice.builder()
                        .quizId(quiz.getId())
                        .content(question.choices().get(i))
                        .correct(i == question.answerIndex())
                        .build());
            }
            savedQuizzes.add(quiz);
        }
        return savedQuizzes;
    }

    private boolean isValidAiQuestion(AiQuizQuestion question, Set<Long> validHeritageIds) {
        return question != null
                && question.heritageId() != null
                && validHeritageIds.contains(question.heritageId())
                && question.title() != null
                && !question.title().isBlank()
                && question.content() != null
                && !question.content().isBlank()
                && question.choices() != null
                && question.choices().size() == 4
                && question.answerIndex() >= 0
                && question.answerIndex() < 4
                && question.explanation() != null
                && !question.explanation().isBlank();
    }

    private String normalizeDifficulty(String difficulty) {
        if (difficulty == null) {
            return "MEDIUM";
        }
        return switch (difficulty) {
            case "EASY", "MEDIUM", "HARD" -> difficulty;
            default -> "MEDIUM";
        };
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
