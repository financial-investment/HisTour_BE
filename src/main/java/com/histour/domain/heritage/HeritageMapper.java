package com.histour.domain.heritage;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HeritageMapper {
    int count();
    void insert(Heritage heritage);
    Long findIdByCode(@Param("kdcd") String kdcd, @Param("asno") String asno, @Param("ctcd") String ctcd);
    void insertMedia(HeritageMedia media);
    void insertDescription(HeritageDescription description);
    List<Heritage> findNearby(@Param("lat") double lat, @Param("lng") double lng);
}
