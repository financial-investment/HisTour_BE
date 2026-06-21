package com.histour.api.controller;

import com.histour.common.response.ApiResponse;
import com.histour.domain.report.RecommendService;
import com.histour.domain.report.dto.RecommendedHeritage;
import com.histour.domain.trip.TripService;
import com.histour.domain.trip.dto.TripCreateRequest;
import com.histour.domain.trip.dto.TripResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Trip", description = "여행 API")
@RestController
@RequestMapping("/api/trip")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;
    private final RecommendService recommendService;

    @Operation(summary = "내 여행 목록", description = "로그인한 사용자의 여행 목록을 최신순으로 반환합니다.")
    @GetMapping
    public ApiResponse<List<TripResponse>> getMyTrips(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(tripService.getMyTrips(userId));
    }

    @Operation(summary = "여행 생성", description = "새 여행을 시작합니다. 생성된 tripId를 반환합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createTrip(
            Authentication authentication,
            @RequestBody TripCreateRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        Long tripId = tripService.createTrip(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(tripId));
    }

    @Operation(summary = "여행 완료 처리", description = "진행 중인 여행을 완료 상태로 변경합니다.")
    @PatchMapping("/{tripId}/complete")
    public ApiResponse<Void> completeTrip(
            Authentication authentication,
            @PathVariable Long tripId) {
        Long userId = (Long) authentication.getPrincipal();
        tripService.completeTrip(tripId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "여행 조회", description = "여행 정보와 방문 기록 목록을 반환합니다.")
    @GetMapping("/{tripId}")
    public ApiResponse<TripResponse> getTrip(
            Authentication authentication,
            @PathVariable Long tripId) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(tripService.getTrip(tripId, userId));
    }

    @Operation(summary = "진행 중 다음 문화재 추천",
               description = "방문 이력 기반 임베딩 유사도 + 현재 위치 반경 내 미방문 문화재를 추천합니다.")
    @GetMapping("/{tripId}/recommend/next")
    public ApiResponse<List<RecommendedHeritage>> recommendNext(
            Authentication authentication,
            @PathVariable Long tripId,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5") double radiusKm) {
        if (lat < -90 || lat > 90) throw new IllegalArgumentException("유효하지 않은 위도입니다. (-90 ~ 90)");
        if (lng < -180 || lng > 180) throw new IllegalArgumentException("유효하지 않은 경도입니다. (-180 ~ 180)");
        if (radiusKm <= 0) throw new IllegalArgumentException("반경은 0보다 커야 합니다.");
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(recommendService.recommendNearby(tripId, userId, lat, lng, radiusKm));
    }
}
