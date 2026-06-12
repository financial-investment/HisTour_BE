package com.histour.domain.heritage.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HeritageDescription {
    private Long id;
    private Long heritageId;
    private String content;
    private int depthLevel;
    private String topic;
    private Long parentId;
    private String source;
}
