package com.histour.domain.report;

import com.histour.client.GmsAiClient;
import com.histour.domain.heritage.entity.Heritage;
import com.histour.domain.heritage.mapper.HeritageMapper;
import com.histour.domain.report.dto.CourseResponse;
import com.histour.domain.report.dto.ReportResponse;
import com.histour.domain.report.dto.VisitedHeritage;
import com.histour.domain.trip.Trip;
import com.histour.domain.trip.TripMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final TripMapper tripMapper;
    private final HeritageMapper heritageMapper;
    private final CourseService courseService;
    private final GmsAiClient gmsAiClient;

    public ReportResponse generateReport(Long tripId, Long userId) {
        Trip trip = tripMapper.findTripById(tripId);
        if (trip == null) throw new NoSuchElementException("여행을 찾을 수 없습니다.");
        if (!trip.getUserId().equals(userId)) throw new IllegalArgumentException("접근 권한이 없습니다.");

        List<Heritage> visited = heritageMapper.findVisitedByTripId(tripId);

        List<VisitedHeritage> visitedDtos = visited.stream()
                .map(h -> new VisitedHeritage(h.getId(), h.getName(), h.getThumbnailUrl()))
                .toList();

        CourseResponse course = courseService.recommendCourse(tripId);

        List<String> visitedNames = visited.stream().map(Heritage::getName).toList();
        List<String> courseNames = course != null
                ? course.heritages().stream().map(h -> h.name()).toList()
                : List.of();
        String summary = visitedNames.isEmpty() ? "" : gmsAiClient.generateTripSummary(visitedNames, courseNames);

        return new ReportResponse(tripId, visitedDtos, course, summary);
    }
}
