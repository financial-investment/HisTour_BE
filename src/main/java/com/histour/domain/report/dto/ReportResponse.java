package com.histour.domain.report.dto;

import java.util.List;

public record ReportResponse(
        Long tripId,
        List<VisitedHeritage> visitedHeritages,
        CourseResponse course,
        String summary
) {}
