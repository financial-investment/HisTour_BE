package com.histour.domain.trip.dto;

import java.time.LocalDate;

public record TripCreateRequest(
        String title,
        LocalDate tripDate
) {}
