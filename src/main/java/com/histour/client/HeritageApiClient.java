package com.histour.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class HeritageApiClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final XmlMapper xmlMapper = XmlMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Value("${heritage.api.base-url}")
    private String baseUrl;

    @Value("${heritage.api.location-url}")
    private String locationUrl;

    public List<ListItem> fetchList(String ccbaKdcd, String ccbaCtcd, int pageIndex) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/SearchKindOpenapiList.do")
                .queryParam("ccbaKdcd", ccbaKdcd)
                .queryParam("pageUnit", 100)
                .queryParam("pageIndex", pageIndex);
        if (ccbaCtcd != null) builder.queryParam("ccbaCtcd", ccbaCtcd);
        String url = builder.build().toUriString();
        try {
            String xml = restTemplate.getForObject(url, String.class);
            ListResponse response = xmlMapper.readValue(xml, ListResponse.class);
            return response.item != null ? response.item : Collections.emptyList();
        } catch (Exception e) {
            log.error("목록 조회 실패 [kdcd={}, ctcd={}]: {}", ccbaKdcd, ccbaCtcd, e.getMessage());
            return Collections.emptyList();
        }
    }

    public DetailItem fetchDetail(String ccbaKdcd, String ccbaAsno, String ccbaCtcd) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/SearchKindOpenapiDt.do")
                .queryParam("ccbaKdcd", ccbaKdcd)
                .queryParam("ccbaAsno", ccbaAsno)
                .queryParam("ccbaCtcd", ccbaCtcd)
                .build().toUriString();
        try {
            String xml = restTemplate.getForObject(url, String.class);
            DetailResponse response = xmlMapper.readValue(xml, DetailResponse.class);
            return response.item;
        } catch (Exception e) {
            log.error("상세 조회 실패 [{}/{}/{}]: {}", ccbaKdcd, ccbaAsno, ccbaCtcd, e.getMessage());
            return null;
        }
    }

    public LocationItem fetchLocation(String ccbaKdcd, String ccbaAsno, String ccbaCtcd) {
        String url = UriComponentsBuilder.fromHttpUrl(locationUrl)
                .queryParam("ccbaKdcd", ccbaKdcd)
                .queryParam("ccbaAsno", ccbaAsno)
                .queryParam("ccbaCtcd", ccbaCtcd)
                .build().toUriString();
        try {
            String xml = restTemplate.getForObject(url, String.class);
            LocationResponse response = xmlMapper.readValue(xml, LocationResponse.class);
            return response.item;
        } catch (Exception e) {
            log.error("위치 조회 실패 [{}/{}/{}]: {}", ccbaKdcd, ccbaAsno, ccbaCtcd, e.getMessage());
            return null;
        }
    }

    // 이미지 API는 <item> 하나 안에 필드가 반복되는 비정상 XML → StAX로 직접 파싱
    public List<ImageItem> fetchImages(String ccbaKdcd, String ccbaAsno, String ccbaCtcd) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/SearchImageOpenapi.do")
                .queryParam("ccbaKdcd", ccbaKdcd)
                .queryParam("ccbaAsno", ccbaAsno)
                .queryParam("ccbaCtcd", ccbaCtcd)
                .build().toUriString();
        try {
            String xml = restTemplate.getForObject(url, String.class);
            return parseImageXml(xml);
        } catch (Exception e) {
            log.error("이미지 조회 실패 [{}/{}/{}]: {}", ccbaKdcd, ccbaAsno, ccbaCtcd, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<ImageItem> parseImageXml(String xml) {
        List<ImageItem> result = new ArrayList<>();
        try {
            // 비표준 한글/특수문자 태그 제거 (API가 가끔 문화재명을 태그로 사용)
            String cleaned = xml.replaceAll("<[^>]*[가-힣·\\.\\(\\)][^>]*>", "");

            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_COALESCING, true);
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(cleaned));

            String currentElement = null;
            String pendingUrl = null;
            String pendingDesc = null;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    currentElement = reader.getLocalName();
                } else if (event == XMLStreamConstants.CHARACTERS) {
                    String text = reader.getText().trim();
                    if (!text.isEmpty()) {
                        if ("imageUrl".equals(currentElement)) pendingUrl = text;
                        else if ("ccimDesc".equals(currentElement)) pendingDesc = text;
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if ("ccimDesc".equals(reader.getLocalName()) && pendingUrl != null) {
                        ImageItem item = new ImageItem();
                        item.imageUrl = pendingUrl;
                        item.ccimDesc = pendingDesc;
                        result.add(item);
                        pendingUrl = null;
                        pendingDesc = null;
                    }
                    currentElement = null;
                }
            }
            reader.close();
        } catch (Exception e) {
            log.warn("이미지 XML 파싱 중단 ({}건 수집): {}", result.size(), e.getMessage());
        }
        return result;
    }

    // ── Response DTOs ──────────────────────────────────────────────────────────

    @Data
    @JacksonXmlRootElement(localName = "result")
    public static class ListResponse {
        public int totalCnt;
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "item")
        public List<ListItem> item;
    }

    @Data
    public static class ListItem {
        public String ccbaMnm1;   // 국문명
        public String ccbaMnm2;   // 영문명
        public String ccmaName;   // 종목명
        public String ccbaKdcd;
        public String ccbaAsno;
        public String ccbaCtcd;
        public String longitude;
        public String latitude;
    }

    @Data
    @JacksonXmlRootElement(localName = "result")
    public static class DetailResponse {
        public DetailItem item;
    }

    @Data
    public static class DetailItem {
        public String ccbaMnm1;
        public String ccbaMnm2;
        public String ccmaName;
        public String ccbaCndt;   // 시대 (비어있는 경우 있음)
        public String ccceName;   // 지정일 텍스트 (예: "조선 태조 7년") - 시대 보완용
        public String imageUrl;
        public String content;
    }

    @Data
    @JacksonXmlRootElement(localName = "result")
    public static class LocationResponse {
        public LocationItem item;
    }

    @Data
    public static class LocationItem {
        public String longitude;
        public String latitude;
    }

    @Data
    public static class ImageItem {
        public String imageUrl;
        public String ccimDesc;
    }
}
