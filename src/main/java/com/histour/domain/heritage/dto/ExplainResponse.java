package com.histour.domain.heritage.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문화재 해설 응답")
public record ExplainResponse(
        @Schema(description = "식별된 문화재 ID", example = "1")
        Long heritageId,

        @Schema(description = "식별된 문화재 이름", example = "창덕궁")
        String heritageName,

        @Schema(description = "AI 생성 해설 텍스트")
        String explanation,

        @Schema(description = "방문 기록 ID — 심화 해설 요청 시 사용 (tripId 없이 호출 시 null)", example = "5", nullable = true)
        Long visitLogId
) {}
