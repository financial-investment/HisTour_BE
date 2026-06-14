package com.histour.domain.heritage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "문화재 해설 요청")
public record ExplainRequest(
        @Schema(description = "촬영 사진 (Base64 인코딩, data:image/... 프리픽스 포함 가능)", example = "/9j/4AAQSkZJRgAB...")
        @NotBlank String image,

        @Schema(description = "현재 위도", example = "37.5796")
        double lat,

        @Schema(description = "현재 경도", example = "126.9770")
        double lng,

        @Schema(description = "여행 ID (방문 기록 저장 시 필요, 없으면 null)", example = "1", nullable = true)
        Long tripId
) {}
