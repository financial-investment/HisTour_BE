package com.histour.domain.heritage.service;

import com.histour.client.GmsAiClient;
import com.histour.common.RateLimitService;
import com.histour.domain.heritage.dto.ExplainRequest;
import com.histour.domain.heritage.dto.ExplainResponse;
import com.histour.domain.heritage.dto.HeritageDetailResponse;
import com.histour.domain.heritage.entity.HeritageMedia;
import com.histour.domain.heritage.entity.Heritage;
import com.histour.domain.heritage.entity.HeritageDescription;
import com.histour.domain.heritage.mapper.HeritageMapper;
import com.histour.domain.trip.TripMapper;
import com.histour.domain.trip.VisitLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeritageService {

    private final HeritageMapper heritageMapper;
    private final TripMapper tripMapper;
    private final GmsAiClient gmsAiClient;
    private final RateLimitService rateLimitService;

    @Value("${upload.dir}")
    private String uploadDir;

    @Value("${upload.base-url}")
    private String uploadBaseUrl;

    public ExplainResponse explain(ExplainRequest request, Long userId) {
        rateLimitService.checkExplain(userId);

        // 1. 반경 500m 문화재 조회
        List<Heritage> candidates = heritageMapper.findNearby(request.lat(), request.lng());
        if (candidates.isEmpty()) {
            throw new NoSuchElementException("주변 500m 내 문화재를 찾을 수 없습니다.");
        }

        // 2. 각 문화재의 공식 설명 로드
        Map<Long, String> descriptions = new LinkedHashMap<>();
        for (Heritage h : candidates) {
            heritageMapper.findDescriptions(h.getId()).stream()
                    .filter(d -> "OFFICIAL".equals(d.getSource()))
                    .findFirst()
                    .ifPresent(d -> descriptions.put(h.getId(), d.getContent()));
        }

        // 3. GMS AI 호출 — 문화재 식별 + 해설 생성
        GmsAiClient.ExplainResult result = gmsAiClient.explainHeritage(
                request.image(), candidates, descriptions
        );

        if (result.heritageIndex() <= 0 || result.heritageIndex() > candidates.size()) {
            throw new IllegalStateException("사진에서 주변 문화재를 식별할 수 없습니다.");
        }

        Heritage identified = candidates.get(result.heritageIndex() - 1);

        // 4. visit_logs 저장
        Long visitLogId = null;
        if (request.tripId() != null) {
            String photoUrl = savePhoto(request.image());
            VisitLog visitLog = VisitLog.builder()
                    .tripId(request.tripId())
                    .heritageId(identified.getId())
                    .photoUrl(photoUrl)
                    .lat(request.lat())
                    .lng(request.lng())
                    .explanation(result.explanation())
                    .build();
            tripMapper.insertVisitLog(visitLog);
            visitLogId = visitLog.getId();
        }

        return new ExplainResponse(identified.getId(), identified.getName(), result.explanation(), visitLogId);
    }

    public HeritageDetailResponse getDetail(Long heritageId) {
        Heritage heritage = heritageMapper.findById(heritageId);
        if (heritage == null) throw new NoSuchElementException("문화재를 찾을 수 없습니다.");

        String description = heritageMapper.findDescriptions(heritageId).stream()
                .filter(d -> "OFFICIAL".equals(d.getSource()))
                .findFirst()
                .map(HeritageDescription::getContent)
                .orElse(null);

        List<String> mediaUrls = heritageMapper.findMedia(heritageId).stream()
                .map(HeritageMedia::getUrl)
                .toList();

        return new HeritageDetailResponse(
                heritage.getId(), heritage.getName(), heritage.getNameHanja(),
                heritage.getCategory(), heritage.getPeriod(),
                heritage.getLat(), heritage.getLng(), heritage.getThumbnailUrl(),
                description, mediaUrls
        );
    }

    public ExplainResponse explainDeeper(Long heritageId, Long visitLogId) {
        Heritage heritage = heritageMapper.findById(heritageId);
        if (heritage == null) {
            throw new NoSuchElementException("문화재를 찾을 수 없습니다.");
        }

        // visitLog 먼저 검증
        VisitLog visitLog = tripMapper.findVisitLogById(visitLogId);
        if (visitLog == null || visitLog.getExplanation() == null) {
            throw new IllegalStateException("기본 해설이 없습니다. 먼저 해설을 요청하세요.");
        }

        // level 2 캐시 확인
        HeritageDescription cached = heritageMapper.findAiDescription(heritageId, 2);
        if (cached != null) {
            return new ExplainResponse(heritageId, heritage.getName(), cached.getContent(), null);
        }

        // 심화 해설 생성
        String deeperContent = gmsAiClient.generateDeeperExplanation(heritage, visitLog.getExplanation());

        // 심화 해설 저장
        heritageMapper.insertDescription(HeritageDescription.builder()
                .heritageId(heritageId)
                .content(deeperContent)
                .depthLevel(2)
                .topic("AI 심화 해설")
                .source("AI_GENERATED")
                .build());

        return new ExplainResponse(heritageId, heritage.getName(), deeperContent, null);
    }

    private String savePhoto(String base64Image) {
        try {
            // data:image/jpeg;base64,... 프리픽스 제거
            String data = base64Image.contains(",") ? base64Image.split(",", 2)[1] : base64Image;
            byte[] bytes = Base64.getDecoder().decode(data);

            Path dir = Paths.get(uploadDir).toAbsolutePath();
            Files.createDirectories(dir);

            String filename = UUID.randomUUID() + ".jpg";
            Files.write(dir.resolve(filename), bytes);

            return uploadBaseUrl + "/" + filename;
        } catch (IOException e) {
            log.warn("[HeritageService] 사진 저장 실패: {}", e.getMessage());
            return null;
        }
    }
}
