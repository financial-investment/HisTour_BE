package com.histour.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeritageApiClientTest {

    private final HeritageApiClient client = new HeritageApiClient();

    @Test
    @DisplayName("정상 XML - 이미지 2건 파싱")
    void 정상_XML_파싱() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <result>
                    <item>
                        <imageUrl>http://example.com/img1.jpg</imageUrl>
                        <ccimDesc>설명1</ccimDesc>
                        <imageUrl>http://example.com/img2.jpg</imageUrl>
                        <ccimDesc>설명2</ccimDesc>
                    </item>
                </result>
                """;

        List<HeritageApiClient.ImageItem> items = client.parseImageXml(xml);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getImageUrl()).isEqualTo("http://example.com/img1.jpg");
        assertThat(items.get(0).getCcimDesc()).isEqualTo("설명1");
        assertThat(items.get(1).getImageUrl()).isEqualTo("http://example.com/img2.jpg");
        assertThat(items.get(1).getCcimDesc()).isEqualTo("설명2");
    }

    @Test
    @DisplayName("한글 태그 포함 비정상 XML - 한글 태그 제거 후 파싱 성공")
    void 한글_태그_포함_XML_파싱() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <result>
                    <item>
                        <대성전>무시되어야 할 내용</대성전>
                        <imageUrl>http://example.com/img1.jpg</imageUrl>
                        <ccimDesc>설명1</ccimDesc>
                    </item>
                </result>
                """;

        List<HeritageApiClient.ImageItem> items = client.parseImageXml(xml);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getImageUrl()).isEqualTo("http://example.com/img1.jpg");
    }

    @Test
    @DisplayName("빈 item - 결과 없음")
    void 빈_XML_응답() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <result>
                    <item/>
                </result>
                """;

        List<HeritageApiClient.ImageItem> items = client.parseImageXml(xml);

        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("imageUrl 없는 항목 - 저장 안 함")
    void imageUrl_없는_항목_제외() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <result>
                    <item>
                        <ccimDesc>URL 없이 설명만 있는 항목</ccimDesc>
                    </item>
                </result>
                """;

        List<HeritageApiClient.ImageItem> items = client.parseImageXml(xml);

        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("ccimDesc 없는 항목 - imageUrl만 있어도 저장")
    void ccimDesc_없어도_저장() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <result>
                    <item>
                        <imageUrl>http://example.com/img1.jpg</imageUrl>
                        <ccimDesc></ccimDesc>
                    </item>
                </result>
                """;

        List<HeritageApiClient.ImageItem> items = client.parseImageXml(xml);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getImageUrl()).isEqualTo("http://example.com/img1.jpg");
    }

    @Test
    @DisplayName("특수문자 포함 태그 (권1-5) - 제거 후 정상 파싱")
    void 특수문자_태그_포함_XML() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <result>
                    <item>
                        <권1-5>내용</권1-5>
                        <imageUrl>http://example.com/img1.jpg</imageUrl>
                        <ccimDesc>설명1</ccimDesc>
                    </item>
                </result>
                """;

        List<HeritageApiClient.ImageItem> items = client.parseImageXml(xml);

        assertThat(items).hasSize(1);
    }
}
