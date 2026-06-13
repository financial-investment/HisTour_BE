package com.histour.api.controller;

import com.histour.domain.heritage.service.HeritageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/heritage")
@RequiredArgsConstructor
public class HeritageController {

    private final HeritageService heritageService;
}
