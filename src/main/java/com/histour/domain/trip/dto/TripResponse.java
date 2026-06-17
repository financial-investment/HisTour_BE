package com.histour.domain.trip.dto;

import com.histour.domain.trip.VisitLog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TripResponse(
        Long tripId,
        String title,
        LocalDate tripDate,
        String status,
        LocalDateTime createdAt,
        int visitCount,
        List<VisitLog> visitLogs
) {}
