package com.histour.batch;

import com.histour.client.HeritageApiClient;
import com.histour.domain.heritage.mapper.HeritageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeritageDataLoaderTest {

    @Mock
    private HeritageApiClient apiClient;

    @Mock
    private HeritageMapper heritageMapper;

    @InjectMocks
    private HeritageDataLoader loader;

    @Nested
    @DisplayName("시대 매핑 - mapPeriod()")
    class MapPeriodTest {

        @Test
        @DisplayName("괄호 연도 기반 - 조선")
        void 괄호_연도_조선() {
            assertThat(loader.mapPeriod("조선 태조 7년(1398)에 창건")).isEqualTo("JOSEON");
        }

        @Test
        @DisplayName("괄호 연도 기반 - 고려")
        void 괄호_연도_고려() {
            assertThat(loader.mapPeriod("고려 현종(1018)에 중건")).isEqualTo("GORYEO");
        }

        @Test
        @DisplayName("괄호 연도 기반 - 통일신라")
        void 괄호_연도_통일신라() {
            assertThat(loader.mapPeriod("신라 문무왕(676) 때 창건")).isEqualTo("UNIFIED");
        }

        @Test
        @DisplayName("세기 표기 - 9세기 → 통일신라")
        void 세기_표기_통일신라() {
            assertThat(loader.mapPeriod("9세기 통일신라 시대 작품")).isEqualTo("UNIFIED");
        }

        @Test
        @DisplayName("세기 표기 - 17세기 → 조선")
        void 세기_표기_조선() {
            assertThat(loader.mapPeriod("17세기에 제작된 것으로 추정")).isEqualTo("JOSEON");
        }

        @Test
        @DisplayName("키워드 fallback - 삼국(백제)")
        void 키워드_백제() {
            assertThat(loader.mapPeriod("백제 무왕 때 창건")).isEqualTo("THREE_KINGDOMS");
        }

        @Test
        @DisplayName("키워드 fallback - 고구려")
        void 키워드_고구려() {
            assertThat(loader.mapPeriod("고구려 장수왕 때 건립")).isEqualTo("THREE_KINGDOMS");
        }

        @Test
        @DisplayName("키워드 fallback - 일제강점기")
        void 키워드_일제강점기() {
            assertThat(loader.mapPeriod("일제강점기 독립운동 유적")).isEqualTo("JAPANESE");
        }

        @Test
        @DisplayName("키워드 fallback - 선사시대")
        void 키워드_선사시대() {
            assertThat(loader.mapPeriod("신석기 시대 유물")).isEqualTo("PREHISTORIC");
        }

        @Test
        @DisplayName("연도+키워드 혼합 시 연도 우선")
        void 연도_우선_적용() {
            // "조선"이라는 키워드가 있어도 연도(1050) 기준 고려로 매핑
            assertThat(loader.mapPeriod("(1050) 조선시대라는 표현이 잘못 포함")).isEqualTo("GORYEO");
        }

        @Test
        @DisplayName("null → UNKNOWN")
        void null_입력() {
            assertThat(loader.mapPeriod(null)).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("빈 문자열 → UNKNOWN")
        void 빈문자열() {
            assertThat(loader.mapPeriod("")).isEqualTo("UNKNOWN");
        }

        @Test
        @DisplayName("분류 불가 텍스트 → UNKNOWN")
        void 분류_불가() {
            assertThat(loader.mapPeriod("기증자 미상")).isEqualTo("UNKNOWN");
        }
    }

    @Nested
    @DisplayName("연도 추출 - extractEarliestYear()")
    class ExtractEarliestYearTest {

        @Test
        @DisplayName("괄호 연도 단일 추출")
        void 괄호_연도_단일() {
            assertThat(loader.extractEarliestYear("을사년(1605)에 제작")).isEqualTo(1605);
        }

        @Test
        @DisplayName("여러 연도 중 가장 이른 연도 반환")
        void 여러_연도_최솟값() {
            assertThat(loader.extractEarliestYear("(1398) 창건, (1592) 소실, (1867) 중건")).isEqualTo(1398);
        }

        @Test
        @DisplayName("세기 표기 → 중간값 변환 (17세기 → 1650)")
        void 세기_표기_변환() {
            assertThat(loader.extractEarliestYear("17세기 작품")).isEqualTo(1650);
        }

        @Test
        @DisplayName("1세기 → 50")
        void 세기_1세기() {
            assertThat(loader.extractEarliestYear("1세기 제작")).isEqualTo(50);
        }

        @Test
        @DisplayName("연도 없으면 null 반환")
        void 연도_없음() {
            assertThat(loader.extractEarliestYear("설명 없음")).isNull();
        }

        @Test
        @DisplayName("범위 밖 연도(0, 9999) 무시")
        void 범위_밖_연도_무시() {
            assertThat(loader.extractEarliestYear("(0) 또는 (9999)")).isNull();
        }
    }

    @Nested
    @DisplayName("연도 → 시대 경계값 매핑 - periodFromYear()")
    class PeriodFromYearTest {

        @Test
        @DisplayName("삼국 / 통일신라 경계: 667 → THREE_KINGDOMS, 668 → UNIFIED")
        void 삼국_통일신라_경계() {
            assertThat(loader.periodFromYear(667)).isEqualTo("THREE_KINGDOMS");
            assertThat(loader.periodFromYear(668)).isEqualTo("UNIFIED");
        }

        @Test
        @DisplayName("통일신라 / 고려 경계: 917 → UNIFIED, 918 → GORYEO")
        void 통일신라_고려_경계() {
            assertThat(loader.periodFromYear(917)).isEqualTo("UNIFIED");
            assertThat(loader.periodFromYear(918)).isEqualTo("GORYEO");
        }

        @Test
        @DisplayName("고려 / 조선 경계: 1391 → GORYEO, 1392 → JOSEON")
        void 고려_조선_경계() {
            assertThat(loader.periodFromYear(1391)).isEqualTo("GORYEO");
            assertThat(loader.periodFromYear(1392)).isEqualTo("JOSEON");
        }

        @Test
        @DisplayName("조선 / 대한제국 경계: 1896 → JOSEON, 1897 → OPENING")
        void 조선_대한제국_경계() {
            assertThat(loader.periodFromYear(1896)).isEqualTo("JOSEON");
            assertThat(loader.periodFromYear(1897)).isEqualTo("OPENING");
        }

        @Test
        @DisplayName("대한제국 / 일제강점기 경계: 1909 → OPENING, 1910 → JAPANESE")
        void 대한제국_일제강점기_경계() {
            assertThat(loader.periodFromYear(1909)).isEqualTo("OPENING");
            assertThat(loader.periodFromYear(1910)).isEqualTo("JAPANESE");
        }

        @Test
        @DisplayName("일제강점기 / 근현대 경계: 1944 → JAPANESE, 1945 → MODERN")
        void 일제강점기_근현대_경계() {
            assertThat(loader.periodFromYear(1944)).isEqualTo("JAPANESE");
            assertThat(loader.periodFromYear(1945)).isEqualTo("MODERN");
        }
    }

    @Nested
    @DisplayName("데이터 로더 동작 - run()")
    class LoaderBehaviorTest {

        @Test
        @DisplayName("데이터가 이미 있으면 적재 건너뜀")
        void 데이터_이미_존재시_스킵() throws Exception {
            when(heritageMapper.count()).thenReturn(1);

            loader.run(null);

            verify(apiClient, never()).fetchList(anyString(), any(), anyInt());
        }

        @Test
        @DisplayName("GPS 없는 항목은 DB 저장 안 함")
        void GPS_없는_항목_스킵() throws Exception {
            HeritageApiClient.ListItem item = new HeritageApiClient.ListItem();
            item.setCcbaKdcd("11");
            item.setCcbaAsno("1234567890123");
            item.setCcbaCtcd("11");
            item.setCcbaMnm1("테스트 문화재");
            item.setLatitude(null);
            item.setLongitude(null);

            HeritageApiClient.DetailItem detail = new HeritageApiClient.DetailItem();
            detail.setCcbaCndt("조선시대");

            when(heritageMapper.count()).thenReturn(0);
            when(apiClient.fetchList(eq("11"), any(), eq(1))).thenReturn(List.of(item));
            when(apiClient.fetchDetail(any(), any(), any())).thenReturn(detail);
            when(apiClient.fetchLocation(any(), any(), any())).thenReturn(null);
            for (String kdcd : new String[]{"12","13","14","15","16","21","23","24"}) {
                when(apiClient.fetchList(eq(kdcd), any(), eq(1))).thenReturn(Collections.emptyList());
            }

            loader.run(null);

            verify(heritageMapper, never()).insert(any());
        }

        @Test
        @DisplayName("정상 항목 - heritage + description + media 모두 저장")
        void 정상_항목_전체_저장() throws Exception {
            HeritageApiClient.ListItem item = new HeritageApiClient.ListItem();
            item.setCcbaKdcd("11");
            item.setCcbaAsno("1234567890123");
            item.setCcbaCtcd("11");
            item.setCcbaMnm1("경복궁");
            item.setLatitude("37.5796");
            item.setLongitude("126.9770");

            HeritageApiClient.DetailItem detail = new HeritageApiClient.DetailItem();
            detail.setCcbaCndt("조선시대");
            detail.setContent("조선 태조 3년(1394)에 창건된 궁궐이다.");
            detail.setImageUrl("http://example.com/thumb.jpg");

            HeritageApiClient.ImageItem img = new HeritageApiClient.ImageItem();
            img.setImageUrl("http://example.com/img1.jpg");
            img.setCcimDesc("정면 전경");

            when(heritageMapper.count()).thenReturn(0);
            when(apiClient.fetchList(eq("11"), any(), eq(1))).thenReturn(List.of(item));
            when(apiClient.fetchDetail(any(), any(), any())).thenReturn(detail);
            when(apiClient.fetchImages(any(), any(), any())).thenReturn(List.of(img));
            when(heritageMapper.findIdByCode(any(), any(), any())).thenReturn(1L);
            for (String kdcd : new String[]{"12","13","14","15","16","21","23","24"}) {
                when(apiClient.fetchList(eq(kdcd), any(), eq(1))).thenReturn(Collections.emptyList());
            }

            loader.run(null);

            verify(heritageMapper).insert(any());
            verify(heritageMapper).insertDescription(any());
            verify(heritageMapper).insertMedia(any());
        }

        @Test
        @DisplayName("이미지 5장 초과 시 5장만 저장")
        void 이미지_최대_5장_제한() throws Exception {
            HeritageApiClient.ListItem item = new HeritageApiClient.ListItem();
            item.setCcbaKdcd("11");
            item.setCcbaAsno("1234567890123");
            item.setCcbaCtcd("11");
            item.setCcbaMnm1("경복궁");
            item.setLatitude("37.5796");
            item.setLongitude("126.9770");

            HeritageApiClient.DetailItem detail = new HeritageApiClient.DetailItem();
            detail.setCcbaCndt("조선시대");

            List<HeritageApiClient.ImageItem> images = new java.util.ArrayList<>();
            for (int i = 1; i <= 8; i++) {
                HeritageApiClient.ImageItem img = new HeritageApiClient.ImageItem();
                img.setImageUrl("http://example.com/img" + i + ".jpg");
                img.setCcimDesc("설명" + i);
                images.add(img);
            }

            when(heritageMapper.count()).thenReturn(0);
            when(apiClient.fetchList(eq("11"), any(), eq(1))).thenReturn(List.of(item));
            when(apiClient.fetchDetail(any(), any(), any())).thenReturn(detail);
            when(apiClient.fetchImages(any(), any(), any())).thenReturn(images);
            when(heritageMapper.findIdByCode(any(), any(), any())).thenReturn(1L);
            for (String kdcd : new String[]{"12","13","14","15","16","21","23","24"}) {
                when(apiClient.fetchList(eq(kdcd), any(), eq(1))).thenReturn(Collections.emptyList());
            }

            loader.run(null);

            verify(heritageMapper, times(5)).insertMedia(any());
        }
    }
}
