package com.histour.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class HeritageApiClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${heritage.api.base-url}")
    private String baseUrl;

    @Value("${heritage.api.location-url}")
    private String locationUrl;
}
