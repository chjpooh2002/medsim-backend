package com.medsim.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 건강보험심사평가원 병원정보서비스 API를 호출해 반경 내 내과 병원 수를 반환한다.
 * API가 radius 파라미터로 서버 측 필터링을 지원하므로 클라이언트 계산 불필요.
 * API 호출 실패 또는 비정상 resultCode 시 -1을 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitorService {

    private static final String API_URL =
            "http://apis.data.go.kr/B551182/hospInfoService1/getHospBasisList1";
    private static final int RADIUS_METER = 2000;
    private static final int NUM_OF_ROWS  = 100;
    private static final String DEPT_CODE = "01"; // 내과

    private final RestTemplate restTemplate;

    @Value("${hira.api.key}")
    private String apiKey;

    /**
     * 지정 좌표 반경 2km 내 내과 병원 수를 반환한다.
     *
     * @param lat 위도
     * @param lng 경도
     * @return 병원 수, API 오류 시 -1
     */
    public int countCompetitors(double lat, double lng) {
        try {
            String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            String url = API_URL
                    + "?ServiceKey=" + encodedKey
                    + "&pageNo=1"
                    + "&numOfRows=" + NUM_OF_ROWS
                    + "&dgsbjtCd=" + DEPT_CODE
                    + "&xPos=" + lng
                    + "&yPos=" + lat
                    + "&radius=" + RADIUS_METER;

            String xml = restTemplate.getForObject(new URI(url), String.class);
            return parseCompetitorCount(xml);

        } catch (Exception e) {
            log.warn("HIRA API 호출 실패 (lat={}, lng={}): {}", lat, lng, e.getMessage());
            return -1;
        }
    }

    private int parseCompetitorCount(String xml) throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));

        // resultCode 확인 — "00"이 아니면 오류
        NodeList resultCodeNodes = doc.getElementsByTagName("resultCode");
        if (resultCodeNodes.getLength() > 0) {
            String code = resultCodeNodes.item(0).getTextContent().trim();
            if (!"00".equals(code)) {
                log.warn("HIRA API 오류 응답 resultCode={}", code);
                return -1;
            }
        }

        // totalCount 우선 사용 (item 수보다 정확)
        NodeList totalCountNodes = doc.getElementsByTagName("totalCount");
        if (totalCountNodes.getLength() > 0) {
            String raw = totalCountNodes.item(0).getTextContent().trim();
            if (!raw.isEmpty()) {
                return Integer.parseInt(raw);
            }
        }

        // totalCount 없으면 item 개수로 대체
        return doc.getElementsByTagName("item").getLength();
    }
}
