package com.histour.domain.report.dto;

public record CourseHeritage(
        Long heritageId,
        String name,
        String thumbnailUrl,
        double lat,
        double lng,
        int order
) {}
