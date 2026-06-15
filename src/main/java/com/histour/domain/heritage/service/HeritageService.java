package com.histour.domain.heritage.service;

import com.histour.client.GmsAiClient;
import com.histour.domain.heritage.dto.ExplainRequest;
import com.histour.domain.heritage.dto.ExplainResponse;
import com.histour.domain.heritage.entity.Heritage;
import com.histour.domain.heritage.entity.HeritageDescription;
import com.histour.domain.heritage.mapper.HeritageMapper;
import com.histour.domain.trip.TripMapper;
import com.histour.domain.trip.VisitLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class HeritageService {

    private final HeritageMapper heritageMapper;
    private final TripMapper tripMapper;
    private final GmsAiClient gmsAiClient;

    public ExplainResponse explain(ExplainRequest request) {
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
            VisitLog visitLog = VisitLog.builder()
                    .tripId(request.tripId())
                    .heritageId(identified.getId())
                    .lat(request.lat())
                    .lng(request.lng())
                    .explanation(result.explanation())
                    .build();
            tripMapper.insertVisitLog(visitLog);
            visitLogId = visitLog.getId();
        }

        return new ExplainResponse(identified.getId(), identified.getName(), result.explanation(), visitLogId);
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
}
