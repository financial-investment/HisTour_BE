package com.histour.domain.heritage.entity;

import lombok.Data;

@Data
public class HeritageMedia {
    private Long id;
    private Long heritageId;
    private String url;
    private String caption;
    private String source;
}
