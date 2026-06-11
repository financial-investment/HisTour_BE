package com.histour.domain.heritage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HeritageService {

    private final HeritageMapper heritageMapper;
}
