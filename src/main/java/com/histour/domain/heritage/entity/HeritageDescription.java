package com.histour.domain.heritage.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeritageDescription {
    private Long id;
    private Long heritageId;
    private String content;
    private int depthLevel;
    private String topic;
    private Long parentId;
    private String source;
}
