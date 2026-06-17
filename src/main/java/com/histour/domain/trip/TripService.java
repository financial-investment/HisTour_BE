package com.histour.domain.trip;

import com.histour.domain.trip.dto.TripCreateRequest;
import com.histour.domain.trip.dto.TripResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.histour.common.exception.ForbiddenException;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripMapper tripMapper;

    @Transactional(readOnly = true)
    public List<TripResponse> getMyTrips(Long userId) {
        return tripMapper.findTripsByUserId(userId).stream()
                .map(t -> new TripResponse(
                        t.getId(), t.getTitle(), t.getTripDate(),
                        t.getStatus(), t.getCreatedAt(), t.getVisitCount(), null))
                .toList();
    }

    @Transactional
    public Long createTrip(Long userId, TripCreateRequest request) {
        if (tripMapper.countInProgressByUserId(userId) > 0) {
            throw new IllegalStateException("이미 진행 중인 여행이 있습니다.");
        }
        LocalDate today = LocalDate.now();
        Trip trip = Trip.builder()
                .userId(userId)
                .title(request.title() != null && !request.title().isBlank()
                        ? request.title()
                        : today + " 역사 여행")
                .tripDate(request.tripDate() != null ? request.tripDate() : today)
                .build();
        tripMapper.insertTrip(trip);
        return trip.getId();
    }

    @Transactional
    public void completeTrip(Long tripId, Long userId) {
        Trip trip = findAndValidateOwner(tripId, userId);
        if ("COMPLETED".equals(trip.getStatus())) {
            throw new IllegalStateException("이미 완료된 여행입니다.");
        }
        tripMapper.updateTripStatus(tripId, "COMPLETED");
    }

    @Transactional(readOnly = true)
    public TripResponse getTrip(Long tripId, Long userId) {
        Trip trip = findAndValidateOwner(tripId, userId);
        List<VisitLog> visitLogs = tripMapper.findVisitLogsByTripId(tripId);
        return new TripResponse(
                trip.getId(), trip.getTitle(), trip.getTripDate(),
                trip.getStatus(), trip.getCreatedAt(), visitLogs.size(), visitLogs);
    }

    private Trip findAndValidateOwner(Long tripId, Long userId) {
        Trip trip = tripMapper.findTripById(tripId);
        if (trip == null) {
            throw new NoSuchElementException("여행을 찾을 수 없습니다.");
        }
        if (!trip.getUserId().equals(userId)) {
            throw new ForbiddenException("접근 권한이 없습니다.");
        }
        return trip;
    }
}
