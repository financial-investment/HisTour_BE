package com.histour.batch;

import com.histour.client.GmsAiClient;
import com.histour.domain.heritage.entity.Heritage;
import com.histour.domain.heritage.mapper.HeritageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.schemafields.VectorField;
import redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "embedding.loader.enabled", havingValue = "true")
public class EmbeddingLoader implements ApplicationRunner {

    public static final String INDEX_NAME = "heritage_emb_idx";
    public static final String KEY_PREFIX = "heritage:emb:";
    public static final int VECTOR_DIM = 1536;

    private static final Map<String, String> PERIOD_NAMES = Map.ofEntries(
            Map.entry("PREHISTORIC", "선사시대"), Map.entry("GOJOSEON", "고조선"),
            Map.entry("THREE_KINGDOMS", "삼국시대"), Map.entry("UNIFIED", "통일신라"),
            Map.entry("GORYEO", "고려"), Map.entry("JOSEON", "조선"),
            Map.entry("OPENING", "개항기"), Map.entry("JAPANESE", "일제강점기"),
            Map.entry("MODERN", "근현대"), Map.entry("UNKNOWN", "시대 미상")
    );

    private final HeritageMapper heritageMapper;
    private final GmsAiClient gmsAiClient;
    private final JedisPooled jedisPooled;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[EmbeddingLoader] 인덱스 초기화 시작");
        ensureIndex();

        List<Heritage> heritages = heritageMapper.findAllForEmbedding();
        log.info("[EmbeddingLoader] 총 {}건 처리 예정", heritages.size());

        int saved = 0;
        for (Heritage h : heritages) {
            String key = KEY_PREFIX + h.getId();
            try {
                String text = buildEmbeddingText(h);
                List<Float> embedding = gmsAiClient.createEmbedding(text);

                Map<byte[], byte[]> fields = new HashMap<>();
                fields.put("heritage_id".getBytes(StandardCharsets.UTF_8), String.valueOf(h.getId()).getBytes(StandardCharsets.UTF_8));
                fields.put("name".getBytes(StandardCharsets.UTF_8), h.getName().getBytes(StandardCharsets.UTF_8));
                fields.put("lat".getBytes(StandardCharsets.UTF_8), String.valueOf(h.getLat()).getBytes(StandardCharsets.UTF_8));
                fields.put("lng".getBytes(StandardCharsets.UTF_8), String.valueOf(h.getLng()).getBytes(StandardCharsets.UTF_8));
                fields.put("ccba_ctcd".getBytes(StandardCharsets.UTF_8), (h.getCcbaCtcd() != null ? h.getCcbaCtcd() : "").getBytes(StandardCharsets.UTF_8));
                fields.put("embedding".getBytes(StandardCharsets.UTF_8), toBytes(embedding));
                jedisPooled.hset(key.getBytes(), fields);
                saved++;

                if (saved % 100 == 0) {
                    log.info("[EmbeddingLoader] {}건 저장 완료", saved);
                }
            } catch (Exception e) {
                log.warn("[EmbeddingLoader] heritageId={} 실패: {}", h.getId(), e.getMessage());
            }
        }
        log.info("[EmbeddingLoader] 완료 — 저장: {}건", saved);
    }

    void ensureIndex() {
        try {
            jedisPooled.ftInfo(INDEX_NAME);
            log.info("[EmbeddingLoader] 기존 인덱스 사용: {}", INDEX_NAME);
        } catch (Exception e) {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("TYPE", "FLOAT32");
            attrs.put("DIM", VECTOR_DIM);
            attrs.put("DISTANCE_METRIC", "COSINE");

            jedisPooled.ftCreate(INDEX_NAME,
                    FTCreateParams.createParams()
                            .on(IndexDataType.HASH)
                            .prefix(KEY_PREFIX),
                    VectorField.builder()
                            .fieldName("embedding")
                            .algorithm(VectorAlgorithm.FLAT)
                            .attributes(attrs)
                            .build()
            );
            log.info("[EmbeddingLoader] 인덱스 생성 완료: {}", INDEX_NAME);
        }
    }

    private static final int DESC_LIMIT = 800;

    private String buildEmbeddingText(Heritage h) {
        StringBuilder sb = new StringBuilder(h.getName());
        if (h.getNameHanja() != null && !h.getNameHanja().isBlank()) {
            sb.append(" (").append(h.getNameHanja()).append(")");
        }
        if (h.getCategory() != null) sb.append(" ").append(h.getCategory());
        if (h.getPeriod() != null) sb.append(" ").append(PERIOD_NAMES.getOrDefault(h.getPeriod(), h.getPeriod()));
        if (h.getDescription() != null && !h.getDescription().isBlank()) {
            String desc = h.getDescription().trim();
            sb.append(" ").append(desc.length() > DESC_LIMIT ? desc.substring(0, DESC_LIMIT) : desc);
        }
        return sb.toString();
    }

    public static byte[] toBytes(List<Float> floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.size() * 4).order(ByteOrder.LITTLE_ENDIAN);
        floats.forEach(buffer::putFloat);
        return buffer.array();
    }
}
