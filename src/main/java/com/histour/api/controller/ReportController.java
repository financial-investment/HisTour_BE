package com.histour.api.controller;

import com.histour.common.response.ApiResponse;
import com.histour.domain.report.ReportService;
import com.histour.domain.report.dto.ReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Report", description = "여행 리포트 API")
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "여행 리포트 조회",
               description = "완료된 여행의 방문 문화재 목록, 다음 여행 추천(다른 지역), AI 요약을 반환합니다.")
    @GetMapping("/{tripId}")
    public ApiResponse<ReportResponse> getReport(
            Authentication authentication,
            @PathVariable Long tripId) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(reportService.generateReport(tripId, userId));
    }
}
