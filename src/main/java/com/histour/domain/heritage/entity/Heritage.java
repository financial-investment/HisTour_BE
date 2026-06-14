package com.histour.domain.heritage.entity;

import lombok.Data;

@Data
public class Heritage {
    private Long id;
    private String name;
    private String nameHanja;
    private String category;
    private String period;
    private double lat;
    private double lng;
    private String thumbnailUrl;
    private String ccbaKdcd;
    private String ccbaAsno;
    private String ccbaCtcd;
}
