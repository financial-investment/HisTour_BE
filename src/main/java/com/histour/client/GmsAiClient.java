package com.histour.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.histour.common.exception.GmsApiException;
import com.histour.domain.heritage.dto.ExplainTopic;
import com.histour.domain.heritage.entity.Heritage;
import com.histour.domain.quiz.dto.AiQuizGenerateRequest;
import com.histour.domain.quiz.dto.AiQuizQuestion;
import com.histour.domain.quiz.dto.AiVisitedHeritage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GmsAiClient {

    private static final int MAX_IMAGE_BYTES = 15_000;
    private static final int MAX_DIM = 512;
    private static final float JPEG_QUALITY = 0.7f;
    private static final int DESC_LIMIT = 800;

    private static final Map<ExplainTopic, String> TOPIC_PROMPTS = Map.of(
            ExplainTopic.STORY,
            """
            이 문화재에 얽힌 잘 알려지지 않은 비화나 일화를 중심으로 해설하세요.
            역사 교과서나 일반 안내서에 나오지 않는 흥미로운 이야기를 발굴해주세요.
            출처가 불분명한 이야기는 "전해지기로는", "~라는 기록이 있습니다" 같은 표현으로 구분하세요.
            최소 600자.""",

            ExplainTopic.PERSON,
            """
            이 문화재와 가장 깊이 연관된 실존 인물에 집중해서 해설하세요.
            그 인물의 삶, 내면의 갈등, 결단의 순간, 그리고 이 장소와의 인연을 생생하게 그려주세요.
            단순한 인물 소개가 아니라 이 문화재와의 관계를 통해 그 인물을 입체적으로 보여주세요.
            최소 600자.""",

            ExplainTopic.ARCHITECTURE,
            """
            지금 방문객이 눈앞에서 볼 수 있는 건축적·예술적 특징을 전문가 시각으로 해설하세요.
            일반 방문객이 그냥 지나쳤을 디테일, 당대 최고 기술의 흔적, 상징적 의미를 담은 구조물에 집중하세요.
            현장에서 실제로 보면서 이해할 수 있도록 구체적이고 생생하게 서술하세요.
            최소 600자.""",

            ExplainTopic.CONTEXT,
            """
            이 문화재가 만들어진 시대의 정치·사회·문화적 배경을 깊이 파고들어 해설하세요.
            당시의 권력 구조, 사회 분위기, 이 문화재가 그 시대에 가졌던 의미를 설명하고,
            역사의 흐름 속에서 이 장소가 어떤 위치를 차지했는지 보여주세요.
            최소 600자.""",

            ExplainTopic.MODERN,
            """
            이 문화재가 오늘날 한국 사회와 문화에 미치는 영향을 이야기해주세요.
            현대에 재발견된 가치, 관련 논쟁이나 연구, 대중문화 속 영향,
            혹은 방문객이 이 장소에서 현대의 시각으로 느낄 수 있는 것들을 다뤄주세요.
            최소 600자."""
    );

    private static final Map<String, String> PERIOD_NAMES = Map.ofEntries(
            Map.entry("PREHISTORIC", "선사시대"),
            Map.entry("GOJOSEON", "고조선"),
            Map.entry("THREE_KINGDOMS", "삼국시대"),
            Map.entry("UNIFIED", "통일신라"),
            Map.entry("GORYEO", "고려"),
            Map.entry("JOSEON", "조선"),
            Map.entry("OPENING", "개항기"),
            Map.entry("JAPANESE", "일제강점기"),
            Map.entry("MODERN", "근현대"),
            Map.entry("UNKNOWN", "시대 미상")
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gms.api.url}")
    private String apiUrl;

    @Value("${gms.api.anthropic-url}")
    private String anthropicUrl;

    @Value("${gms.api.embedding-url}")
    private String embeddingUrl;

    @Value("${gms.api.key}")
    private String apiKey;

    public int classifyHeritage(String base64Image, List<Heritage> candidates) {
        String compressedBase64 = compressImage(base64Image);
        String imageDataUrl = "data:image/jpeg;base64," + compressedBase64;

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", "gpt-4o");
            body.put("max_tokens", 50);

            ArrayNode messages = objectMapper.createArrayNode();

            ObjectNode systemMsg = objectMapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", "사진과 주변 문화재 목록을 보고 사진 속 문화재의 번호를 찾아주세요. 반드시 JSON 형식으로만 응답하세요.");
            messages.add(systemMsg);

            StringBuilder sb = new StringBuilder("주변 500m 내 문화재 후보:\n");
            for (int i = 0; i < candidates.size(); i++) {
                Heritage h = candidates.get(i);
                String periodKo = PERIOD_NAMES.getOrDefault(h.getPeriod(), h.getPeriod());
                sb.append(String.format("[%d] %s", i + 1, h.getName()));
                if (h.getNameHanja() != null && !h.getNameHanja().isBlank()) {
                    sb.append(" (").append(h.getNameHanja()).append(")");
                }
                sb.append(String.format(" — %s · %s\n", h.getCategory(), periodKo));
            }
            sb.append("\n사진 속 문화재가 목록에 있으면 해당 번호를, 없으면 0을 반환하세요.\n");
            sb.append("{\"heritageIndex\": <번호>}");

            ArrayNode contentArray = objectMapper.createArrayNode();
            contentArray.add(imageUrlNode(imageDataUrl));
            contentArray.add(textNode(sb.toString()));

            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.set("content", contentArray);
            messages.add(userMsg);
            body.set("messages", messages);

            String requestBody = objectMapper.writeValueAsString(body);
            log.info("[GMS 분류 요청] 후보 {}개, body {} bytes", candidates.size(), requestBody.getBytes(StandardCharsets.UTF_8).length);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("GMS 분류 API 오류 [{}]: {}", response.statusCode(), response.body());
                throw new GmsApiException("GMS API 오류: " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new GmsApiException("GMS 분류 응답에 choices가 없습니다.");
            }
            String content = choices.get(0).path("message").path("content").asText();
            String json = extractJson(content);
            int index = objectMapper.readTree(json).path("heritageIndex").asInt(0);
            JsonNode usage = root.path("usage");
            log.info("[GMS 분류 결과] heritageIndex={} | 토큰: prompt={}, completion={}, total={}",
                    index, usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt(), usage.path("total_tokens").asInt());
            return index;

        } catch (GmsApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("GMS 분류 API 호출 실패: {}", e.getMessage());
            throw new GmsApiException("문화재 분류에 실패했습니다.", e);
        }
    }

    public String generateBasicExplanation(Heritage heritage, String officialDescription) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", "gpt-4o");
            body.put("max_tokens", 1500);

            ArrayNode messages = objectMapper.createArrayNode();

            ObjectNode systemMsg = objectMapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content",
                    "당신은 20년 경력의 한국 역사 현장 해설사입니다. " +
                    "역사 교과서에 없는 생생한 이야기로 방문객을 그 시대 속으로 데려가는 것이 당신의 특기입니다.\n\n" +
                    "절대 하지 말아야 할 것:\n" +
                    "- \"이 문화재는 ~년에 지어졌습니다\" 같은 백과사전식 사실 나열\n" +
                    "- 연도와 명칭만 열거하는 방식\n" +
                    "- \"~로 알려져 있습니다\", \"~로 불립니다\" 같은 딱딱한 수동 문어체\n" +
                    "- 도입부를 항상 같은 문장 패턴으로 시작하는 것");
            messages.add(systemMsg);

            String periodKo = PERIOD_NAMES.getOrDefault(heritage.getPeriod(), heritage.getPeriod());
            StringBuilder sb = new StringBuilder();
            sb.append("문화재: ").append(heritage.getName());
            if (heritage.getNameHanja() != null && !heritage.getNameHanja().isBlank()) {
                sb.append(" (").append(heritage.getNameHanja()).append(")");
            }
            sb.append(String.format(" — %s · %s\n\n", heritage.getCategory(), periodKo));

            if (officialDescription != null && !officialDescription.isBlank()) {
                String trimmed = officialDescription.length() > DESC_LIMIT
                        ? officialDescription.substring(0, DESC_LIMIT) + "..."
                        : officialDescription;
                sb.append("참고 설명:\n").append(trimmed).append("\n\n");
            }

            sb.append("""
                    위 문화재에 대해 현장 방문객을 위한 해설을 작성해주세요.
                    소제목이나 단계 레이블 없이 하나의 흐르는 이야기로 해설을 작성하세요.
                    아래 흐름을 자연스럽게 녹여서 써주세요:

                    - 이 장소에서 일어난 가장 극적인 역사적 순간을 현재 시제로 생생하게 묘사하며 시작할 것. 매번 다른 방식으로 시작하세요.
                    - 이 문화재와 깊이 연관된 실존 인물이나 구체적 사건을 중심으로 이야기를 전개할 것. 그 인물의 감정과 결단을 살려줄 것.
                    - 당시 사람들이 이 장소를 어떻게 느꼈을지, 시대적 분위기 속 의미를 담을 것.
                    - 지금 방문객이 눈앞에서 볼 수 있는 특징 하나를 콕 짚어 그 의미를 설명할 것.
                    - 과거와 현재를 잇는 문장으로 마무리할 것.

                    분량: 700자 이상
                    반드시 아래 JSON 형식으로만 응답하세요:
                    {"explanation": "<해설>"}""");

            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", sb.toString());
            messages.add(userMsg);
            body.set("messages", messages);

            String requestBody = objectMapper.writeValueAsString(body);
            log.info("[GMS 기본해설 요청] {}", heritage.getName());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("GMS 기본해설 API 오류 [{}]: {}", response.statusCode(), response.body());
                throw new GmsApiException("GMS API 오류: " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new GmsApiException("GMS 기본해설 응답에 choices가 없습니다.");
            }
            String content = choices.get(0).path("message").path("content").asText();
            JsonNode usage = root.path("usage");
            log.info("[GMS 기본해설 결과] 토큰: prompt={}, completion={}, total={}",
                    usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt(), usage.path("total_tokens").asInt());
            String json = extractJson(content);
            String explanation = objectMapper.readTree(json).path("explanation").asText("");
            if (explanation.isBlank()) throw new GmsApiException("AI 해설 내용이 비어있습니다.");
            return explanation;

        } catch (GmsApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("GMS 기본해설 API 호출 실패: {}", e.getMessage());
            throw new GmsApiException("AI 해설 생성에 실패했습니다.", e);
        }
    }

    private String compressImage(String base64OrDataUrl) {
        String base64 = base64OrDataUrl.startsWith("data:")
                ? base64OrDataUrl.substring(base64OrDataUrl.indexOf(',') + 1)
                : base64OrDataUrl;

        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64);
            if (imageBytes.length <= MAX_IMAGE_BYTES) {
                log.info("[이미지] {}KB — 압축 생략", imageBytes.length / 1024);
                return base64;
            }

            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (img == null) {
                log.warn("[이미지] 디코딩 실패 — 원본 사용");
                return base64;
            }

            int w = img.getWidth(), h = img.getHeight();
            if (w > MAX_DIM || h > MAX_DIM) {
                double scale = (double) MAX_DIM / Math.max(w, h);
                int nw = (int) (w * scale), nh = (int) (h * scale);
                Image scaled = img.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
                BufferedImage resized = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = resized.createGraphics();
                g.drawImage(scaled, 0, 0, null);
                g.dispose();
                img = resized;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(img, null, null), param);
            }
            writer.dispose();

            byte[] compressed = baos.toByteArray();
            log.info("[이미지 압축] {}KB → {}KB", imageBytes.length / 1024, compressed.length / 1024);
            return Base64.getEncoder().encodeToString(compressed);
        } catch (Exception e) {
            log.warn("[이미지 압축] 실패 — 원본 사용: {}", e.getMessage());
            return base64;
        }
    }


    private ObjectNode imageUrlNode(String url) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "image_url");
        ObjectNode urlNode = objectMapper.createObjectNode();
        urlNode.put("url", url);
        node.set("image_url", urlNode);
        return node;
    }

    private ObjectNode textNode(String text) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "text");
        node.put("text", text);
        return node;
    }

    public String generateDeeperExplanation(Heritage heritage, String existingExplanation, ExplainTopic topic) {
        String periodKo = PERIOD_NAMES.getOrDefault(heritage.getPeriod(), heritage.getPeriod());
        String requestBody = buildDeeperRequestBody(heritage, periodKo, existingExplanation, topic);

        log.info("[GMS 심화해설 요청] {} (Claude Haiku)", heritage.getName());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(anthropicUrl))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("GMS Anthropic API 오류 [{}]: {}", response.statusCode(), response.body());
                throw new GmsApiException("GMS API 오류: " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentArray = root.path("content");
            if (!contentArray.isArray() || contentArray.isEmpty()) {
                throw new GmsApiException("GMS Anthropic 응답에 content가 없습니다.");
            }
            String content = contentArray.get(0).path("text").asText();
            String json = extractJson(content);
            return objectMapper.readTree(json).path("explanation").asText("");
        } catch (GmsApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("GMS 심화해설 API 호출 실패: {}", e.getMessage());
            throw new GmsApiException("AI 심화 해설 생성에 실패했습니다.", e);
        }
    }

    private String buildDeeperRequestBody(Heritage heritage, String periodKo, String existingExplanation, ExplainTopic topic) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", "claude-haiku-4-5-20251001");
            body.put("max_tokens", 2048);
            body.put("system",
                    "당신은 한국 역사 현장 전문 해설사입니다. " +
                    "방문객이 기본 해설을 이미 들었습니다. " +
                    "기본 해설에서 다룬 내용은 절대 반복하지 말고, " +
                    "완전히 새로운 관점이나 심화 내용만 이야기해주세요.");

            StringBuilder sb = new StringBuilder();
            sb.append("문화재: ").append(heritage.getName());
            if (heritage.getNameHanja() != null && !heritage.getNameHanja().isBlank()) {
                sb.append(" (").append(heritage.getNameHanja()).append(")");
            }
            sb.append(" — ").append(heritage.getCategory()).append(" · ").append(periodKo).append("\n\n");
            sb.append("방문객이 이미 들은 기본 해설:\n").append(existingExplanation).append("\n\n");

            if (topic != null) {
                sb.append("방문객이 선택한 심화 주제: [").append(topicLabel(topic)).append("]\n\n");
                sb.append(TOPIC_PROMPTS.get(topic));
            } else {
                sb.append("""
                        위 기본 해설을 이미 들은 방문객에게 심화 해설을 들려주세요.
                        아래 4가지를 모두 포함하세요:
                        1. 잘 알려지지 않은 비화나 일화
                        2. 이 문화재와 연관된 다른 역사적 사건이나 인물
                        3. 전문가 시각에서 본 건축·예술적 특징의 세부 사항
                        4. 이 장소가 현대에 갖는 의미나 영향
                        최소 600자.""");
            }
            sb.append("\n반드시 아래 JSON 형식으로만 응답하세요:\n{\"explanation\": \"<심화 해설>\"}");

            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", sb.toString());
            messages.add(userMsg);
            body.set("messages", messages);

            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("요청 직렬화 실패", e);
        }
    }

    public List<Float> createEmbedding(String text) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", "text-embedding-3-small");
            body.put("input", text);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(embeddingUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new GmsApiException("임베딩 API 오류: " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new GmsApiException("임베딩 응답에 data가 없습니다.");
            }
            JsonNode embedding = data.get(0).path("embedding");
            List<Float> result = new ArrayList<>();
            for (JsonNode v : embedding) {
                result.add((float) v.asDouble());
            }
            return result;
        } catch (GmsApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GmsApiException("임베딩 생성 실패", e);
        }
    }

    public String generateTripSummary(List<String> visitedNames, List<String> recommendedNames) {
        try {
            StringBuilder userContent = new StringBuilder();
            userContent.append("방문한 문화재: ").append(String.join(", ", visitedNames)).append("\n");
            if (!recommendedNames.isEmpty()) {
                userContent.append("다음 여행 추천 문화재: ").append(String.join(", ", recommendedNames)).append("\n");
            }
            userContent.append("""

                    위 여행 기록을 바탕으로 여행 요약과 다음 여행 추천 문구를 작성해주세요.
                    반드시 아래 JSON 형식으로만 응답하세요:
                    {"summary": "<2~3문장의 여행 요약 및 다음 여행 추천 문구>"}""");

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", "gpt-4o");
            body.put("max_tokens", 500);

            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode systemMsg = objectMapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", "당신은 한국 역사 여행 큐레이터입니다. 방문 문화재를 바탕으로 여행의 의미와 다음 여행 추천을 따뜻하고 감성적으로 표현해주세요.");
            messages.add(systemMsg);

            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", userContent.toString());
            messages.add(userMsg);
            body.set("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new GmsApiException("여행 요약 API 오류: " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new GmsApiException("GMS 여행요약 응답에 choices가 없습니다.");
            }
            String content = choices.get(0).path("message").path("content").asText();
            String json = extractJson(content);
            return objectMapper.readTree(json).path("summary").asText("");
        } catch (GmsApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GmsApiException("여행 요약 생성 실패", e);
        }
    }

    public List<AiQuizQuestion> generateQuestions(AiQuizGenerateRequest request) {
        String requestBody = buildQuizRequestBody(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(anthropicUrl))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        log.info("[GMS 퀴즈 요청] {}문제 생성 (Claude Haiku)", request.count());
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("GMS Quiz API 오류 [{}]: {}", response.statusCode(), response.body());
                throw new GmsApiException("GMS API 오류: " + response.statusCode());
            }
            return parseQuizResponse(response.body());
        } catch (GmsApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("GMS Quiz API 호출 실패: {}", e.getMessage());
            throw new GmsApiException("AI 퀴즈 생성에 실패했습니다.", e);
        }
    }

    private String buildQuizRequestBody(AiQuizGenerateRequest request) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", "claude-haiku-4-5-20251001");
            body.put("max_tokens", 4096);
            body.put("system",
                    "당신은 한국사 교육용 퀴즈 출제자입니다. " +
                    "사용자가 방문한 문화재와 현장 해설을 바탕으로 4지선다 복습 퀴즈를 만듭니다. " +
                    "정답은 반드시 하나여야 하며, 사실 검증이 어려운 내용은 피하세요. " +
                    "선지는 짧은 명사형 표현으로 작성하고, 쉼표, 물음표, 느낌표 같은 특수기호는 사용하지 마세요.");

            StringBuilder prompt = new StringBuilder();
            prompt.append("생성할 문제 수: ").append(request.count()).append("\n\n");
            prompt.append("방문 유적지:\n");
            for (AiVisitedHeritage heritage : request.visitedHeritages()) {
                prompt.append("- heritageId: ").append(heritage.heritageId()).append("\n");
                prompt.append("  장소명: ").append(heritage.heritageName()).append("\n");
                if (heritage.explanation() != null && !heritage.explanation().isBlank()) {
                    String trimmed = heritage.explanation().length() > 700
                            ? heritage.explanation().substring(0, 700) + "..."
                            : heritage.explanation();
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
            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", prompt.toString());
            messages.add(userMsg);
            body.set("messages", messages);

            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("퀴즈 생성 요청 직렬화 실패", e);
        }
    }

    private List<AiQuizQuestion> parseQuizResponse(String responseBody) {
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

    private String topicLabel(ExplainTopic topic) {
        return switch (topic) {
            case STORY -> "비화·일화";
            case PERSON -> "핵심 인물";
            case ARCHITECTURE -> "건축·예술 디테일";
            case CONTEXT -> "시대적 배경";
            case MODERN -> "현대적 의미";
        };
    }

    private String extractJson(String content) {
        // 마크다운 코드블록 제거 (```json ... ``` 또는 ``` ... ```)
        String trimmed = content.strip();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end).strip();
            }
        }
        // JSON 객체 시작 위치 찾기
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}
