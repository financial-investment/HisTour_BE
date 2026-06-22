package com.histour.domain.report;

import com.histour.client.GmsAiClient;
import com.histour.common.exception.ForbiddenException;
import com.histour.domain.heritage.entity.Heritage;
import com.histour.domain.heritage.mapper.HeritageMapper;
import com.histour.domain.report.dto.CourseResponse;
import com.histour.domain.report.dto.ReportResponse;
import com.histour.domain.report.dto.VisitedHeritage;
import com.histour.domain.trip.Trip;
import com.histour.domain.trip.TripMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final String SUMMARY_CACHE_PREFIX = "report:summary:";

    private final TripMapper tripMapper;
    private final HeritageMapper heritageMapper;
    private final CourseService courseService;
    private final GmsAiClient gmsAiClient;
    private final RedisTemplate<String, String> redisTemplate;

    public ReportResponse generateReport(Long tripId, Long userId) {
        Trip trip = tripMapper.findTripById(tripId);
        if (trip == null) throw new NoSuchElementException("여행을 찾을 수 없습니다.");
        if (!trip.getUserId().equals(userId)) throw new ForbiddenException("접근 권한이 없습니다.");
        if (!"COMPLETED".equals(trip.getStatus())) throw new IllegalStateException("완료된 여행만 리포트를 조회할 수 있습니다.");

        List<Heritage> visited = heritageMapper.findVisitedByTripId(tripId);

        List<VisitedHeritage> visitedDtos = visited.stream()
                .map(h -> new VisitedHeritage(h.getId(), h.getName(), h.getThumbnailUrl()))
                .toList();

        CourseResponse course = courseService.recommendCourse(tripId);

        String cacheKey = SUMMARY_CACHE_PREFIX + tripId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return new ReportResponse(tripId, visitedDtos, course, cached);
        }

        List<String> visitedNames = visited.stream().map(Heritage::getName).toList();
        List<String> courseNames = course != null
                ? course.heritages().stream().map(h -> h.name()).toList()
                : List.of();
        String summary = visitedNames.isEmpty() ? "" : gmsAiClient.generateTripSummary(visitedNames, courseNames);
        redisTemplate.opsForValue().set(cacheKey, summary);

        return new ReportResponse(tripId, visitedDtos, course, summary);
    }
}
