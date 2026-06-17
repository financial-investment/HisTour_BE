package com.histour.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.histour.common.exception.GmsApiException;
import com.histour.domain.heritage.entity.Heritage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
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

    public record ExplainResult(int heritageIndex, String explanation) {}

    public ExplainResult explainHeritage(String base64Image,
                                          List<Heritage> candidates,
                                          Map<Long, String> descriptions) {
        String compressedBase64 = compressImage(base64Image);
        String imageDataUrl = "data:image/jpeg;base64," + compressedBase64;
        String requestBody = buildRequestBody("gpt-4o", imageDataUrl, candidates, descriptions);

        log.info("[GMS 요청] 총 body 크기: {} bytes", requestBody.getBytes(StandardCharsets.UTF_8).length);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("GMS API 오류 [{}]: {}", response.statusCode(), response.body());
                throw new GmsApiException("GMS API 오류: " + response.statusCode());
            }
            return parseResponse(response.body());
        } catch (GmsApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("GMS API 호출 실패: {}", e.getMessage());
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
                resized.createGraphics().drawImage(scaled, 0, 0, null);
                img = resized;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(ImageIO.createImageOutputStream(baos));
            writer.write(null, new IIOImage(img, null, null), param);
            writer.dispose();

            byte[] compressed = baos.toByteArray();
            log.info("[이미지 압축] {}KB → {}KB", imageBytes.length / 1024, compressed.length / 1024);
            return Base64.getEncoder().encodeToString(compressed);
        } catch (Exception e) {
            log.warn("[이미지 압축] 실패 — 원본 사용: {}", e.getMessage());
            return base64;
        }
    }

    private String buildRequestBody(String model, String imageDataUrl,
                                    List<Heritage> candidates,
                                    Map<Long, String> descriptions) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);

            ArrayNode messages = objectMapper.createArrayNode();

            ObjectNode systemMsg = objectMapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content",
                    "당신은 한국 역사 현장 전문 해설사입니다. " +
                    "방문객이 찍은 사진 속 문화재를 식별하고, 그 역사적 이야기를 생동감 있게 들려주세요. " +
                    "단순한 정보 나열이 아니라 현장에서 실제로 일어난 사건, 인물, 시대의 분위기를 중심으로 " +
                    "마치 현장에서 직접 안내하듯 한국어로 서술하세요. " +
                    "해설은 반드시 '지금 당신이 서 있는 이 곳에서는'으로 시작해야 합니다.");
            messages.add(systemMsg);

            ArrayNode contentArray = objectMapper.createArrayNode();
            contentArray.add(imageUrlNode(imageDataUrl));

            StringBuilder sb = new StringBuilder();
            sb.append("주변 500m 내 문화재 후보 목록:\n\n");
            for (int i = 0; i < candidates.size(); i++) {
                Heritage h = candidates.get(i);
                String periodKo = PERIOD_NAMES.getOrDefault(h.getPeriod(), h.getPeriod());
                sb.append(String.format("[%d] %s", i + 1, h.getName()));
                if (h.getNameHanja() != null && !h.getNameHanja().isBlank()) {
                    sb.append(" (").append(h.getNameHanja()).append(")");
                }
                sb.append(String.format(" — %s · %s\n", h.getCategory(), periodKo));
                String desc = descriptions.get(h.getId());
                if (desc != null && !desc.isBlank()) {
                    String trimmed = desc.length() > DESC_LIMIT ? desc.substring(0, DESC_LIMIT) + "..." : desc;
                    sb.append("참고 설명: ").append(trimmed).append("\n");
                }
                sb.append("\n");
            }
            sb.append("""
                    위 사진에서 보이는 문화재를 목록에서 찾아 주세요.

                    찾은 문화재에 대해 아래 조건을 모두 지켜 해설을 작성하세요:
                    1. 현장감: "지금 당신이 서 있는 이 곳에서는..." 처럼 방문객이 현장에 있는 듯한 도입
                    2. 핵심 사건·인물: 이 문화재와 관련된 대표적인 역사적 사건이나 인물 이야기
                    3. 시대 맥락: 그 시대의 정치·사회적 배경
                    4. 문화재만의 특징: 건축·예술·역사적으로 주목할 만한 점
                    5. 분량: 최소 500자

                    일치하는 문화재가 없으면 heritageIndex를 0으로 반환하세요.
                    반드시 아래 JSON 형식으로만 응답하세요:
                    {"heritageIndex": <번호>, "explanation": "<해설>"}""");
            contentArray.add(textNode(sb.toString()));

            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.set("content", contentArray);
            messages.add(userMsg);

            body.set("messages", messages);
            body.put("max_tokens", 1000);

            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("요청 직렬화 실패", e);
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

    public String generateDeeperExplanation(Heritage heritage, String existingExplanation) {
        String periodKo = PERIOD_NAMES.getOrDefault(heritage.getPeriod(), heritage.getPeriod());
        String requestBody = buildDeeperRequestBody(heritage, periodKo, existingExplanation);

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

    private String buildDeeperRequestBody(Heritage heritage, String periodKo, String existingExplanation) {
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
            sb.append("""
                    위 기본 해설을 이미 들은 방문객에게 심화 해설을 들려주세요.
                    다음 중 하나 이상을 반드시 포함하세요:
                    1. 잘 알려지지 않은 비화나 일화
                    2. 이 문화재와 연관된 다른 역사적 사건이나 인물
                    3. 전문가 시각에서 본 건축·예술적 특징의 세부 사항
                    4. 이 장소가 현대에 갖는 의미나 영향
                    최소 500자. 반드시 아래 JSON 형식으로만 응답하세요:
                    {"explanation": "<심화 해설>"}""");

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

    private ExplainResult parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new GmsApiException("GMS 응답에 choices가 없습니다.");
            }
            String content = choices.get(0).path("message").path("content").asText();
            String json = extractJson(content);
            JsonNode result = objectMapper.readTree(json);
            int index = result.path("heritageIndex").asInt(0);
            String explanation = result.path("explanation").asText("");
            return new ExplainResult(index, explanation);
        } catch (GmsApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("GMS 응답 파싱 실패: {}", responseBody);
            throw new GmsApiException("AI 응답 파싱에 실패했습니다.", e);
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
                throw new GmsApiException("여행 요약 응답에 choices가 없습니다.");
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
