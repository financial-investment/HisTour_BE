package com.histour.domain.report;

import com.histour.batch.EmbeddingLoader;
import com.histour.domain.heritage.entity.Heritage;
import com.histour.domain.heritage.mapper.HeritageMapper;
import com.histour.domain.report.dto.RecommendedHeritage;
import com.histour.domain.trip.Trip;
import com.histour.domain.trip.TripMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.FTSearchParams;
import redis.clients.jedis.search.SearchResult;

import redis.clients.jedis.args.SortingOrder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {

    private final HeritageMapper heritageMapper;
    private final TripMapper tripMapper;
    private final JedisPooled jedisPooled;

    public List<RecommendedHeritage> recommendNearby(Long tripId, Long userId, double lat, double lng, double radiusKm) {
        Trip trip = tripMapper.findTripById(tripId);
        if (trip == null) throw new NoSuchElementException("여행을 찾을 수 없습니다.");
        if (!trip.getUserId().equals(userId)) throw new IllegalArgumentException("접근 권한이 없습니다.");

        List<Heritage> visited = heritageMapper.findVisitedByTripId(tripId);
        if (visited.isEmpty()) return List.of();

        Set<Long> visitedIds = visited.stream().map(Heritage::getId).collect(Collectors.toSet());

        List<Float> avgVector = computeAverageEmbedding(visitedIds);
        if (avgVector == null) {
            log.warn("[추천] 임베딩 데이터 없음 — tripId={}", tripId);
            return List.of();
        }

        return knnSearch(avgVector, 50).stream()
                .filter(id -> !visitedIds.contains(id))
                .map(id -> buildRecommended(id, lat, lng))
                .filter(Objects::nonNull)
                .filter(r -> r.distanceM() <= radiusKm * 1000)
                .sorted(Comparator.comparingInt(RecommendedHeritage::distanceM))
                .limit(5)
                .collect(Collectors.toList());
    }

    public List<Float> computeAverageEmbedding(Set<Long> heritageIds) {
        List<List<Float>> vectors = new ArrayList<>();
        for (Long id : heritageIds) {
            byte[] raw = jedisPooled.hget(
                    (EmbeddingLoader.KEY_PREFIX + id).getBytes(),
                    "embedding".getBytes()
            );
            if (raw != null) vectors.add(toFloatList(raw));
        }
        if (vectors.isEmpty()) return null;

        int dim = vectors.get(0).size();
        float[] sum = new float[dim];
        for (List<Float> v : vectors) {
            for (int i = 0; i < dim; i++) sum[i] += v.get(i);
        }
        List<Float> avg = new ArrayList<>(dim);
        for (float s : sum) avg.add(s / vectors.size());
        return avg;
    }

    public List<Long> knnSearch(List<Float> queryVector, int k) {
        byte[] queryVec = EmbeddingLoader.toBytes(queryVector);
        String query = String.format("*=>[KNN %d @embedding $vec AS score]", k);
        FTSearchParams params = FTSearchParams.searchParams()
                .addParam("vec", queryVec)
                .sortBy("score", SortingOrder.ASC)
                .returnFields("heritage_id")
                .dialect(2)
                .limit(0, k);
        try {
            SearchResult result = jedisPooled.ftSearch(EmbeddingLoader.INDEX_NAME, query, params);
            return result.getDocuments().stream()
                    .map(doc -> Long.parseLong(doc.getString("heritage_id")))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[KNN 검색 실패]: {}", e.getMessage());
            return List.of();
        }
    }

    private RecommendedHeritage buildRecommended(Long id, double userLat, double userLng) {
        Map<byte[], byte[]> rawFields = jedisPooled.hgetAll((EmbeddingLoader.KEY_PREFIX + id).getBytes());
        if (rawFields.isEmpty()) return null;

        Map<String, String> fields = new HashMap<>();
        rawFields.forEach((k, v) -> fields.put(new String(k), new String(v)));

        double hLat = Double.parseDouble(fields.getOrDefault("lat", "0"));
        double hLng = Double.parseDouble(fields.getOrDefault("lng", "0"));
        int distM = (userLat == 0 && userLng == 0) ? 0 : (int) haversineMeters(userLat, userLng, hLat, hLng);

        Heritage heritage = heritageMapper.findById(id);
        String thumbnailUrl = heritage != null ? heritage.getThumbnailUrl() : null;

        return new RecommendedHeritage(id, fields.getOrDefault("name", ""), thumbnailUrl, hLat, hLng, distM);
    }

    private String getField(Long id, String field) {
        byte[] val = jedisPooled.hget(
                (EmbeddingLoader.KEY_PREFIX + id).getBytes(),
                field.getBytes()
        );
        return val == null ? null : new String(val);
    }

    private List<Float> toFloatList(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        List<Float> result = new ArrayList<>(bytes.length / 4);
        while (buffer.hasRemaining()) result.add(buffer.getFloat());
        return result;
    }

    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
