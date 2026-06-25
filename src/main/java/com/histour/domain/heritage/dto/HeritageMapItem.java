package com.histour.domain.heritage.dto;

public record HeritageMapItem(
        Long heritageId,
        String name,
        double lat,
        double lng,
        String thumbnailUrl
) {}
