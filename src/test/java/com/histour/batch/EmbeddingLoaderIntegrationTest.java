package com.histour.batch;

import com.histour.client.GmsAiClient;
import com.histour.domain.heritage.entity.Heritage;
import com.histour.domain.heritage.mapper.HeritageMapper;
import com.histour.domain.report.RecommendService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import redis.clients.jedis.JedisPooled;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Slf4j
@SpringBootTest(properties = {
        "embedding.loader.enabled=false",
        "heritage.loader.enabled=false",
        "spring.main.web-application-type=none"
})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/histour?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul"
})
class EmbeddingLoaderIntegrationTest {

    @Autowired private HeritageMapper heritageMapper;
    @Autowired private GmsAiClient gmsAiClient;
    @Autowired private JedisPooled jedisPooled;
    @Autowired private RecommendService recommendService;

    private static final String TEST_IDX = EmbeddingLoader.INDEX_NAME;
    private static final String TEST_PREFIX = EmbeddingLoader.KEY_PREFIX;

    private final List<String> createdKeys = new ArrayList<>();

    @AfterEach
    void cleanup() {
        createdKeys.forEach(key -> jedisPooled.del(key));
        createdKeys.clear();
        log.info("테스트 완료 — Redis 키 {}건 정리됨", createdKeys.size());
    }

    @Test
    void 임베딩_생성_저장_KNN검색_5건_테스트() {
        // 1. heritage 5건 조회
        List<Heritage> all = heritageMapper.findAllForEmbedding();
        assertThat(all).hasSizeGreaterThanOrEqualTo(5);
        List<Heritage> samples = all.subList(0, 5);
        log.info("테스트 대상: {}", samples.stream().map(Heritage::getName).toList());

        // 2. 인덱스 생성 (없으면)
        try { jedisPooled.ftInfo(TEST_IDX); }
        catch (Exception e) {
            java.util.Map<String, Object> attrs = new java.util.HashMap<>();
            attrs.put("TYPE", "FLOAT32");
            attrs.put("DIM", EmbeddingLoader.VECTOR_DIM);
            attrs.put("DISTANCE_METRIC", "COSINE");
            jedisPooled.ftCreate(TEST_IDX,
                    redis.clients.jedis.search.FTCreateParams.createParams()
                            .on(redis.clients.jedis.search.IndexDataType.HASH)
                            .prefix(TEST_PREFIX),
                    redis.clients.jedis.search.schemafields.VectorField.builder()
                            .fieldName("embedding")
                            .algorithm(redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm.FLAT)
                            .attributes(attrs)
                            .build());
            log.info("[인덱스 생성] {}", TEST_IDX);
        }

        // 3. 임베딩 생성 + Redis 저장
        for (Heritage h : samples) {
            String key = EmbeddingLoader.KEY_PREFIX + h.getId();
            jedisPooled.del(key);
            createdKeys.add(key);

            List<Float> embedding = gmsAiClient.createEmbedding(
                    h.getName() + " " + h.getCategory() + " " + h.getPeriod()
            );
            assertThat(embedding).hasSize(EmbeddingLoader.VECTOR_DIM);
            log.info("[임베딩] {} → {}차원 벡터 생성", h.getName(), embedding.size());

            java.util.Map<byte[], byte[]> fields = new java.util.HashMap<>();
            fields.put("heritage_id".getBytes(), String.valueOf(h.getId()).getBytes());
            fields.put("name".getBytes(), h.getName().getBytes());
            fields.put("lat".getBytes(), String.valueOf(h.getLat()).getBytes());
            fields.put("lng".getBytes(), String.valueOf(h.getLng()).getBytes());
            fields.put("ccba_ctcd".getBytes(), (h.getCcbaCtcd() != null ? h.getCcbaCtcd() : "").getBytes());
            fields.put("embedding".getBytes(), EmbeddingLoader.toBytes(embedding));
            jedisPooled.hset(key.getBytes(), fields);
            log.info("[저장] Redis key={}", key);
        }

        // 4. 평균 벡터로 KNN 검색
        Set<Long> sampleIds = samples.stream().map(Heritage::getId).collect(Collectors.toSet());
        List<Float> queryVec = recommendService.computeAverageEmbedding(sampleIds);
        assertThat(queryVec).isNotNull().hasSize(EmbeddingLoader.VECTOR_DIM);

        List<Long> results = recommendService.knnSearch(queryVec, 5);
        log.info("[KNN 결과] heritageId 목록: {}", results);
        assertThat(results).isNotEmpty();
    }
}
