package com.histour.domain.trip.dto;

import com.histour.domain.trip.VisitLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TripResponse {
    private Long tripId;
    private String title;
    private LocalDate tripDate;
    private String status;
    private LocalDateTime createdAt;
    private int visitCount;
    private List<VisitLog> visitLogs;
}
