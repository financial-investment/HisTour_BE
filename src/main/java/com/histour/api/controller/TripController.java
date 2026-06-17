package com.histour.api.controller;

import com.histour.common.response.ApiResponse;
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
}
