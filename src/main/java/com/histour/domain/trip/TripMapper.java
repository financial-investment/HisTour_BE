package com.histour.domain.trip;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TripMapper {
    void insertTrip(Trip trip);
    Trip findTripById(@Param("id") Long id);
    List<Trip> findTripsByUserId(@Param("userId") Long userId);
    int countInProgressByUserId(@Param("userId") Long userId);
    void updateTripStatus(@Param("id") Long id, @Param("status") String status);
    void insertVisitLog(VisitLog visitLog);
    VisitLog findVisitLogById(@Param("id") Long id);
    List<VisitLog> findVisitLogsByTripId(@Param("tripId") Long tripId);
}
