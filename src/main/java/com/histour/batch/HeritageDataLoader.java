package com.histour.batch;

import com.histour.client.HeritageApiClient;
import com.histour.domain.heritage.HeritageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HeritageDataLoader {

    private final HeritageApiClient apiClient;
    private final HeritageMapper heritageMapper;
}
