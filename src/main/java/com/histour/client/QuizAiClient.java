package com.histour.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.histour.common.exception.GmsApiException;
import com.histour.domain.quiz.dto.AiQuizGenerateRequest;
import com.histour.domain.quiz.dto.AiQuizQuestion;
import com.histour.domain.quiz.dto.AiVisitedHeritage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class QuizAiClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gms.api.anthropic-url}")
    private String anthropicUrl;

    @Value("${gms.api.key}")
    private String apiKey;

    public List<AiQuizQuestion> generateQuestions(AiQuizGenerateRequest request) {
        String requestBody = buildRequestBody(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(anthropicUrl))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("GMS Quiz Anthropic API 오류 [{}]: {}", response.statusCode(), response.body());
                throw new GmsApiException("GMS API 오류: " + response.statusCode());
            }
            return parseResponse(response.body());
        } catch (GmsApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("GMS Quiz API 호출 실패: {}", e.getMessage());
            throw new GmsApiException("AI 퀴즈 생성에 실패했습니다.", e);
        }
    }

    private String buildRequestBody(AiQuizGenerateRequest request) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", "claude-haiku-4-5-20251001");
            body.put("max_tokens", 4096);
            body.put("system",
                    "당신은 한국사 교육용 퀴즈 출제자입니다. " +
                    "사용자가 방문한 문화재와 현장 해설을 바탕으로 4지선다 복습 퀴즈를 만듭니다. " +
                    "정답은 반드시 하나여야 하며, 사실 검증이 어려운 내용은 피하세요.");

            StringBuilder prompt = new StringBuilder();
            prompt.append("생성할 문제 수: ").append(request.count()).append("\n\n");
            prompt.append("방문 유적지:\n");
            for (AiVisitedHeritage heritage : request.visitedHeritages()) {
                prompt.append("- heritageId: ").append(heritage.heritageId()).append("\n");
                prompt.append("  장소명: ").append(heritage.heritageName()).append("\n");
                if (heritage.explanation() != null && !heritage.explanation().isBlank()) {
                    String explanation = heritage.explanation();
                    String trimmed = explanation.length() > 700 ? explanation.substring(0, 700) + "..." : explanation;
                    prompt.append("  방문 해설: ").append(trimmed).append("\n");
                }
            }
            prompt.append("""

                    조건:
                    1. 각 문제는 위 heritageId 중 하나와 반드시 연결하세요.
                    2. choices는 정확히 4개여야 합니다.
                    3. answerIndex는 0부터 시작하는 정답 선택지 인덱스입니다.
                    4. explanation은 정답 근거를 이해할 수 있는 짧은 한국어 해설입니다.
                    5. difficulty는 EASY, MEDIUM, HARD 중 하나입니다.
                    6. content에는 정답을 노골적으로 포함하지 마세요.

                    반드시 아래 JSON 형식으로만 응답하세요:
                    {
                      "questions": [
                        {
                          "heritageId": 1,
                          "title": "경복궁 복습",
                          "content": "다음 중 경복궁에 대한 설명으로 옳은 것은?",
                          "choices": ["선택지1", "선택지2", "선택지3", "선택지4"],
                          "answerIndex": 0,
                          "explanation": "해설",
                          "difficulty": "MEDIUM"
                        }
                      ]
                    }""");

            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", prompt.toString());
            messages.add(userMessage);
            body.set("messages", messages);

            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("퀴즈 생성 요청 직렬화 실패", e);
        }
    }

    private List<AiQuizQuestion> parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contentArray = root.path("content");
            if (!contentArray.isArray() || contentArray.isEmpty()) {
                throw new GmsApiException("GMS Anthropic 응답에 content가 없습니다.");
            }

            String content = contentArray.get(0).path("text").asText();
            JsonNode questions = objectMapper.readTree(extractJson(content)).path("questions");
            if (!questions.isArray()) {
                throw new GmsApiException("AI 퀴즈 응답에 questions가 없습니다.");
            }

            List<AiQuizQuestion> result = new ArrayList<>();
            for (JsonNode node : questions) {
                List<String> choices = new ArrayList<>();
                for (JsonNode choice : node.path("choices")) {
                    choices.add(choice.asText());
                }
                result.add(new AiQuizQuestion(
                        node.path("heritageId").asLong(),
                        node.path("title").asText("여행 복습 퀴즈"),
                        node.path("content").asText(),
                        choices,
                        node.path("answerIndex").asInt(-1),
                        node.path("explanation").asText(""),
                        node.path("difficulty").asText("MEDIUM")
                ));
            }
            return result;
        } catch (GmsApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 퀴즈 응답 파싱 실패: {}", responseBody);
            throw new GmsApiException("AI 퀴즈 응답 파싱에 실패했습니다.", e);
        }
    }

    private String extractJson(String content) {
        String trimmed = content.strip();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end).strip();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}
