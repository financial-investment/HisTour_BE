package com.histour.domain.trip;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TripMapper {
    void insertVisitLog(VisitLog visitLog);
    VisitLog findVisitLogById(@Param("id") Long id);
}
