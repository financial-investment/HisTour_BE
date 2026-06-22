package com.histour.domain.heritage.dto;

import java.util.List;

public record HeritageDetailResponse(
        Long heritageId,
        String name,
        String nameHanja,
        String category,
        String period,
        double lat,
        double lng,
        String thumbnailUrl,
        String description,
        List<String> mediaUrls
) {}
