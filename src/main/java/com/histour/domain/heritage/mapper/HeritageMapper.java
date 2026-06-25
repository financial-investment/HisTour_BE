package com.histour.domain.heritage.mapper;

import com.histour.domain.heritage.dto.HeritageCategoryStats;
import com.histour.domain.heritage.dto.HeritageMapItem;
import com.histour.domain.heritage.entity.Heritage;
import com.histour.domain.heritage.entity.HeritageDescription;
import com.histour.domain.heritage.entity.HeritageMedia;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HeritageMapper {
    int count();
    List<HeritageCategoryStats> countByCategory();
    void insert(Heritage heritage);
    Long findIdByCode(@Param("kdcd") String kdcd, @Param("asno") String asno, @Param("ctcd") String ctcd);
    void insertMedia(HeritageMedia media);
    void insertDescription(HeritageDescription description);
    Heritage findById(@Param("id") Long id);
    List<Heritage> findByIds(@Param("ids") List<Long> ids);
    List<Heritage> findNearby(@Param("lat") double lat, @Param("lng") double lng);
    List<HeritageMapItem> findByBounds(@Param("swLat") double swLat, @Param("swLng") double swLng,
                                       @Param("neLat") double neLat, @Param("neLng") double neLng);
    List<Heritage> findNearestByLocation(@Param("lat") double lat, @Param("lng") double lng,
                                         @Param("radiusM") double radiusM, @Param("limit") int limit);
    List<Heritage> findAllForEmbedding();
    List<Heritage> findVisitedByTripId(@Param("tripId") Long tripId);
    List<HeritageDescription> findDescriptions(@Param("heritageId") Long heritageId);
    HeritageDescription findOfficialDescription(@Param("heritageId") Long heritageId);
    List<HeritageMedia> findMedia(@Param("heritageId") Long heritageId);
    HeritageDescription findAiDescription(@Param("heritageId") Long heritageId, @Param("depthLevel") int depthLevel, @Param("topic") String topic);
}
