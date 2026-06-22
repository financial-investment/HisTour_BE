package com.histour.domain.report;

import com.histour.batch.EmbeddingLoader;
import com.histour.domain.heritage.entity.Heritage;
import com.histour.domain.heritage.mapper.HeritageMapper;
import com.histour.domain.report.dto.CourseHeritage;
import com.histour.domain.report.dto.CourseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private static final double CLUSTER_RADIUS_KM = 25.0;
    private static final double MIN_DISTANCE_FROM_VISITED_KM = 50.0;

    private static final Map<String, String> REGION_NAMES = Map.ofEntries(
            Map.entry("11", "서울"),
            Map.entry("21", "부산"),
            Map.entry("22", "대구"),
            Map.entry("23", "인천"),
            Map.entry("24", "광주"),
            Map.entry("25", "대전"),
            Map.entry("26", "울산"),
            Map.entry("31", "경기"),
            Map.entry("32", "강원"),
            Map.entry("33", "충북"),
            Map.entry("34", "충남"),
            Map.entry("35", "전북"),
            Map.entry("36", "전남"),
            Map.entry("37", "경북"),
            Map.entry("38", "경남"),
            Map.entry("50", "제주")
    );

    private final HeritageMapper heritageMapper;
    private final RecommendService recommendService;
    private final JedisPooled jedisPooled;

    public CourseResponse recommendCourse(Long tripId) {
        List<Heritage> visited = heritageMapper.findVisitedByTripId(tripId);
        if (visited.isEmpty()) return null;

        Set<Long> visitedIds = visited.stream().map(Heritage::getId).collect(Collectors.toSet());

        // 방문 문화재 중심 좌표
        double visitedCenterLat = visited.stream().mapToDouble(Heritage::getLat).average().orElse(0);
        double visitedCenterLng = visited.stream().mapToDouble(Heritage::getLng).average().orElse(0);

        List<Float> avgVector = recommendService.computeAverageEmbedding(visitedIds);
        if (avgVector == null) return null;

        // KNN 100개 → 방문 제외 → CourseHeritage 빌드 (KNN 유사도 순서 유지)
        List<Long> candidateIds = recommendService.knnSearch(avgVector, 100).stream()
                .filter(id -> !visitedIds.contains(id))
                .collect(Collectors.toList());

        if (candidateIds.isEmpty()) return null;

        Map<Long, String> thumbnails = heritageMapper.findByIds(candidateIds).stream()
                .collect(Collectors.toMap(Heritage::getId, h -> h.getThumbnailUrl() != null ? h.getThumbnailUrl() : ""));

        List<CourseHeritage> candidates = candidateIds.stream()
                .map(id -> buildCourseHeritage(id, thumbnails.get(id)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) return null;

        // 각 후보를 중심으로 25km 반경 클러스터 생성 → 방문 지역에서 50km 이상 떨어진 것만
        List<CourseHeritage> bestCluster = List.of();
        CourseHeritage bestCenter = null;

        for (CourseHeritage center : candidates) {
            double distFromVisited = haversineKm(visitedCenterLat, visitedCenterLng, center.lat(), center.lng());
            if (distFromVisited < MIN_DISTANCE_FROM_VISITED_KM) continue;

            List<CourseHeritage> cluster = candidates.stream()
                    .filter(h -> haversineKm(center.lat(), center.lng(), h.lat(), h.lng()) <= CLUSTER_RADIUS_KM)
                    .collect(Collectors.toList());

            if (cluster.size() > bestCluster.size()) {
                bestCluster = cluster;
                bestCenter = center;
            }
        }

        if (bestCluster.isEmpty() || bestCenter == null) return null;

        // KNN 유사도 순서 기준으로 상위 5개 추출
        Set<Long> clusterIds = bestCluster.stream().map(CourseHeritage::heritageId).collect(Collectors.toSet());
        List<CourseHeritage> top5 = candidates.stream()
                .filter(h -> clusterIds.contains(h.heritageId()))
                .limit(5)
                .collect(Collectors.toList());

        // Nearest Neighbor 경로 정렬
        List<CourseHeritage> route = nearestNeighbor(top5);

        // order 부여
        List<CourseHeritage> ordered = new ArrayList<>();
        for (int i = 0; i < route.size(); i++) {
            CourseHeritage h = route.get(i);
            ordered.add(new CourseHeritage(h.heritageId(), h.name(), h.thumbnailUrl(), h.lat(), h.lng(), i + 1));
        }

        // 클러스터 대표 문화재의 ccba_ctcd로 지역명 결정
        String ctcd = getField(bestCenter.heritageId(), "ccba_ctcd");
        String regionName = ctcd != null ? REGION_NAMES.getOrDefault(ctcd, ctcd) : "알 수 없음";

        log.info("[CourseService] tripId={} → 클러스터 중심=({}, {}), 지역={}, 코스={}건",
                tripId, bestCenter.lat(), bestCenter.lng(), regionName, ordered.size());

        return new CourseResponse(ctcd, regionName, ordered);
    }

    private CourseHeritage buildCourseHeritage(Long id, String thumbnailUrl) {
        Map<byte[], byte[]> rawFields = jedisPooled.hgetAll((EmbeddingLoader.KEY_PREFIX + id).getBytes());
        if (rawFields.isEmpty()) return null;

        Map<String, String> fields = new HashMap<>();
        rawFields.forEach((k, v) -> fields.put(new String(k, java.nio.charset.StandardCharsets.UTF_8),
                                               new String(v, java.nio.charset.StandardCharsets.UTF_8)));

        double lat;
        double lng;
        try {
            lat = Double.parseDouble(fields.getOrDefault("lat", "0"));
            lng = Double.parseDouble(fields.getOrDefault("lng", "0"));
        } catch (NumberFormatException e) {
            log.warn("[코스] 좌표 파싱 실패 heritage_id={}", id);
            return null;
        }

        return new CourseHeritage(id, fields.getOrDefault("name", ""), thumbnailUrl, lat, lng, 0);
    }

    private List<CourseHeritage> nearestNeighbor(List<CourseHeritage> heritages) {
        if (heritages.size() <= 1) return heritages;

        List<CourseHeritage> remaining = new ArrayList<>(heritages);
        List<CourseHeritage> route = new ArrayList<>();
        route.add(remaining.remove(0));

        while (!remaining.isEmpty()) {
            CourseHeritage current = route.get(route.size() - 1);
            CourseHeritage nearest = remaining.stream()
                    .min(Comparator.comparingDouble(h -> haversineKm(current.lat(), current.lng(), h.lat(), h.lng())))
                    .orElseThrow(() -> new IllegalStateException("경로 계산 중 문화재 목록이 비어있습니다."));
            remaining.remove(nearest);
            route.add(nearest);
        }
        return route;
    }

    private String getField(Long id, String field) {
        byte[] val = jedisPooled.hget((EmbeddingLoader.KEY_PREFIX + id).getBytes(), field.getBytes());
        return val == null ? null : new String(val, java.nio.charset.StandardCharsets.UTF_8);
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
