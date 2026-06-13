package com.histour.domain.heritage.service;

import com.histour.domain.heritage.mapper.HeritageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HeritageService {

    private final HeritageMapper heritageMapper;
}
