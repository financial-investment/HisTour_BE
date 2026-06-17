package com.histour.domain.heritage.mapper;

import com.histour.domain.heritage.entity.Heritage;
import com.histour.domain.heritage.entity.HeritageDescription;
import com.histour.domain.heritage.entity.HeritageMedia;
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
    Heritage findById(@Param("id") Long id);
    List<Heritage> findNearby(@Param("lat") double lat, @Param("lng") double lng);
    List<Heritage> findAllForEmbedding();
    List<Heritage> findVisitedByTripId(@Param("tripId") Long tripId);
    List<HeritageDescription> findDescriptions(@Param("heritageId") Long heritageId);
    List<HeritageMedia> findMedia(@Param("heritageId") Long heritageId);
    HeritageDescription findAiDescription(@Param("heritageId") Long heritageId, @Param("depthLevel") int depthLevel, @Param("topic") String topic);
}
