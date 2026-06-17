package com.histour.domain.trip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitLog {
    private Long id;
    private Long tripId;
    private Long heritageId;
    private String heritageName;
    private String photoUrl;
    private double lat;
    private double lng;
    private String explanation;
    private LocalDateTime visitedAt;
}
