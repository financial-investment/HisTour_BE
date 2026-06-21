package com.histour.domain.report.dto;

import java.util.List;

public record CourseResponse(
        String regionCode,
        String regionName,
        List<CourseHeritage> heritages
) {}
