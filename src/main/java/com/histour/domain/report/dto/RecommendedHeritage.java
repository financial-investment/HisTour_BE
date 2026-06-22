package com.histour.domain.report.dto;

public record RecommendedHeritage(
        Long heritageId,
        String name,
        String thumbnailUrl,
        double lat,
        double lng,
        int distanceM
) {}
