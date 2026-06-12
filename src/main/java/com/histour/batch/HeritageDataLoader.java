package com.histour.batch;

import com.histour.client.HeritageApiClient;
import com.histour.domain.heritage.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "heritage.loader.enabled", havingValue = "true")
public class HeritageDataLoader implements ApplicationRunner {

    private final HeritageApiClient apiClient;
    private final HeritageMapper heritageMapper;

    // 무형문화재(17, 22)는 GPS 좌표 없어서 제외
    private static final String[] CATEGORIES = {"11", "12", "13", "14", "15", "16", "21", "23", "24"};

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (heritageMapper.count() > 0) {
            log.info("heritage 데이터가 이미 존재합니다. 로더를 건너뜁니다.");
            return;
        }

        log.info("=== 국가유산청 데이터 적재 시작 ===");
        int total = 0;
        for (String kdcd : CATEGORIES) {
            log.info("카테고리 {} 적재 시작", kdcd);
            total += loadCategory(kdcd);
        }
        log.info("=== 적재 완료: {}건 ===", total);
    }

    private int loadCategory(String kdcd) throws InterruptedException {
        int loaded = 0;
        int pageIndex = 1;

        while (true) {
            List<HeritageApiClient.ListItem> items = apiClient.fetchList(kdcd, null, pageIndex);
            if (items.isEmpty()) break;

            for (HeritageApiClient.ListItem item : items) {
                try {
                    loadOne(item);
                    loaded++;
                } catch (Exception e) {
                    log.warn("적재 실패 [{}]: {}", item.getCcbaMnm1(), e.getMessage());
                }
                Thread.sleep(100);
            }

            if (items.size() < 100) break;
            pageIndex++;
        }

        return loaded;
    }

    private void loadOne(HeritageApiClient.ListItem listItem) throws InterruptedException {
        String kdcd = listItem.getCcbaKdcd();
        String asno = listItem.getCcbaAsno();
        String ctcd = listItem.getCcbaCtcd();

        HeritageApiClient.DetailItem detail = apiClient.fetchDetail(kdcd, asno, ctcd);
        Thread.sleep(100);

        double lat = 0, lng = 0;
        if (listItem.getLatitude() != null && !listItem.getLatitude().isBlank()) {
            lat = Double.parseDouble(listItem.getLatitude());
            lng = Double.parseDouble(listItem.getLongitude());
        } else {
            HeritageApiClient.LocationItem loc = apiClient.fetchLocation(kdcd, asno, ctcd);
            Thread.sleep(100);
            if (loc != null && loc.getLatitude() != null && !loc.getLatitude().isBlank()) {
                lat = Double.parseDouble(loc.getLatitude());
                lng = Double.parseDouble(loc.getLongitude());
            }
        }

        if (lat == 0 && lng == 0) {
            log.warn("위치 정보 없음 — 건너뜀: {}", listItem.getCcbaMnm1());
            return;
        }

        String periodRaw = (detail != null && detail.getCcbaCndt() != null && !detail.getCcbaCndt().isBlank())
                ? detail.getCcbaCndt()
                : (detail != null ? detail.getCcceName() : null);

        String period = mapPeriod(periodRaw);
        // ccbaCndt, ccceName 둘 다 실패하면 description 본문에서 한 번 더 시도
        if ("UNKNOWN".equals(period) && detail != null && detail.getContent() != null) {
            period = mapPeriod(detail.getContent());
        }

        Heritage heritage = new Heritage();
        heritage.setName(listItem.getCcbaMnm1());
        heritage.setNameHanja(listItem.getCcbaMnm2());
        heritage.setCategory(listItem.getCcmaName());
        heritage.setPeriod(period);
        heritage.setLat(lat);
        heritage.setLng(lng);
        String imageUrl = (detail != null && detail.getImageUrl() != null && !detail.getImageUrl().isBlank())
                ? detail.getImageUrl() : null;
        heritage.setThumbnailUrl(imageUrl);
        heritage.setCcbaKdcd(kdcd);
        heritage.setCcbaAsno(asno);
        heritage.setCcbaCtcd(ctcd);
        heritageMapper.insert(heritage);

        Long heritageId = heritageMapper.findIdByCode(kdcd, asno, ctcd);
        if (heritageId == null) return;

        if (detail != null && detail.getContent() != null && !detail.getContent().isBlank()) {
            heritageMapper.insertDescription(HeritageDescription.builder()
                    .heritageId(heritageId)
                    .content(detail.getContent())
                    .depthLevel(1)
                    .source("OFFICIAL")
                    .build());
        }

        List<HeritageApiClient.ImageItem> images = apiClient.fetchImages(kdcd, asno, ctcd);
        Thread.sleep(100);
        int imgCount = 0;
        for (HeritageApiClient.ImageItem img : images) {
            if (imgCount >= 5) break;
            if (img.getImageUrl() == null || img.getImageUrl().isBlank()) continue;
            HeritageMedia media = new HeritageMedia();
            media.setHeritageId(heritageId);
            media.setUrl(img.getImageUrl());
            media.setCaption(img.getCcimDesc());
            media.setSource("OFFICIAL");
            heritageMapper.insertMedia(media);
            imgCount++;
        }

        log.info("적재: {} (이미지 {}장)", listItem.getCcbaMnm1(), imgCount);
    }

    private String mapPeriod(String raw) {
        if (raw == null) return "UNKNOWN";

        // 1단계: 연도 추출 → 시대 경계값으로 자동 매핑
        Integer year = extractEarliestYear(raw);
        if (year != null) return periodFromYear(year);

        // 2단계: 연도 없을 때만 키워드 fallback (왕조명/특징어 최소한만)
        if (raw.contains("선사") || raw.contains("구석기") || raw.contains("신석기") || raw.contains("청동기")) return "PREHISTORIC";
        if (raw.contains("고조선") || raw.contains("낙랑") || raw.contains("한사군")) return "GOJOSEON";
        if (raw.contains("통일신라") || raw.contains("남북국") || raw.contains("발해")) return "UNIFIED";
        if (raw.contains("삼국") || raw.contains("고구려") || raw.contains("백제") || raw.contains("신라") || raw.contains("가야")) return "THREE_KINGDOMS";
        if (raw.contains("고려") || raw.contains("청자") || raw.contains("대장경")) return "GORYEO";
        if (raw.contains("조선") || raw.contains("임진왜란") || raw.contains("분청사기") || raw.contains("백자")) return "JOSEON";
        if (raw.contains("대한제국") || raw.contains("개항") || raw.contains("갑오")) return "OPENING";
        if (raw.contains("일제") || raw.contains("강점") || raw.contains("안중근")) return "JAPANESE";
        if (raw.contains("광복") || raw.contains("6·25") || raw.contains("근대")) return "MODERN";
        return "UNKNOWN";
    }

    // 텍스트에서 가장 이른 연도 추출 — (YYYY) 괄호 표기 우선, N세기 표현 보완
    private Integer extractEarliestYear(String text) {
        List<Integer> years = new ArrayList<>();

        // (YYYY) 형태: 역사적 제작연도의 표준 표기 (예: "을사년(1605)", "고종 34년(1247)")
        Matcher m1 = Pattern.compile("\\((\\d{3,4})\\)").matcher(text);
        while (m1.find()) {
            int y = Integer.parseInt(m1.group(1));
            if (y >= 1 && y <= 2024) years.add(y);
        }

        // N세기 형태: 세기 중간값 사용 (예: "17세기" → 1650)
        Matcher m2 = Pattern.compile("(\\d{1,2})세기").matcher(text);
        while (m2.find()) {
            int c = Integer.parseInt(m2.group(1));
            if (c >= 1 && c <= 21) years.add((c - 1) * 100 + 50);
        }

        return years.isEmpty() ? null : Collections.min(years);
    }

    // 연도 → 시대 경계값 매핑
    private String periodFromYear(int year) {
        if (year < 668)  return "THREE_KINGDOMS"; // 삼국 (고조선/선사는 키워드로만)
        if (year < 918)  return "UNIFIED";
        if (year < 1392) return "GORYEO";
        if (year < 1897) return "JOSEON";
        if (year < 1910) return "OPENING";
        if (year < 1945) return "JAPANESE";
        return "MODERN";
    }
}
