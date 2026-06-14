package com.histour.api.controller;

import com.histour.client.GmsAiClient;
import com.histour.common.response.ApiResponse;
import com.histour.domain.heritage.dto.ExplainRequest;
import com.histour.domain.heritage.dto.ExplainResponse;
import com.histour.domain.heritage.service.HeritageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Heritage", description = "문화재 해설 API")
@RestController
@RequestMapping("/api/heritage")
@RequiredArgsConstructor
public class HeritageController {

    private final HeritageService heritageService;
    private final GmsAiClient gmsAiClient;

    @Operation(
        summary = "문화재 기본 해설",
        description = "사진(Base64)과 현재 좌표를 전송하면 반경 500m 내 문화재를 식별하고 AI 해설을 반환합니다. " +
                      "결과는 DB에 캐싱되며, tripId가 있으면 방문 기록도 저장됩니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "해설 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "반경 500m 내 문화재 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "사진에서 주변 문화재를 식별할 수 없음")
    })
    @PostMapping("/explain")
    public ApiResponse<ExplainResponse> explain(@RequestBody @Valid ExplainRequest request) {
        return ApiResponse.ok(heritageService.explain(request));
    }

    @Operation(
        summary = "문화재 심화 해설",
        description = "기본 해설을 기반으로 잘 알려지지 않은 비화, 연관 사건, 전문가 시각 등 심화 내용을 반환합니다. " +
                      "/explain 호출 시 반환된 visitLogId를 함께 전달해야 합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "심화 해설 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "문화재 ID 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "visitLogId에 해당하는 기본 해설 없음")
    })
    @GetMapping("/{heritageId}/explain/deeper")
    public ApiResponse<ExplainResponse> explainDeeper(
            @Parameter(description = "문화재 ID", example = "1") @PathVariable Long heritageId,
            @Parameter(description = "/explain 응답에서 받은 visitLogId", example = "5", required = true) @RequestParam Long visitLogId) {
        return ApiResponse.ok(heritageService.explainDeeper(heritageId, visitLogId));
    }

    @Operation(summary = "GMS API 연결 테스트", hidden = true)
    @GetMapping("/test-gms")
    public ApiResponse<String> testGms() {
        String result = gmsAiClient.testTextOnly();
        return ApiResponse.ok(result);
    }
}
