package com.medsim.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 반경 내 경쟁병원 수를 반환한다.
 * 현재는 동별 하드코딩 Map 사용 (심평원 API 연동 보류).
 * Map에 없는 동은 기본값 3을 반환한다.
 */
@Slf4j
@Service
public class CompetitorService {

    private static final int DEFAULT_COUNT = 3;

    /** 동별 경쟁 내과 병원 수 (반경 2km 기준 추정치) */
    private static final Map<String, Integer> COMPETITOR_MAP = Map.ofEntries(
        Map.entry("역삼1동",    8),
        Map.entry("삼성1동",    7),
        Map.entry("논현1동",    6),
        Map.entry("청담동",     5),
        Map.entry("서초1동",    6),
        Map.entry("방배1동",    4),
        Map.entry("잠실본동",   7),
        Map.entry("문정1동",    4),
        Map.entry("서교동",     5),
        Map.entry("합정동",     4),
        Map.entry("망원1동",    3),
        Map.entry("혜화동",     5),
        Map.entry("사직동",     3),
        Map.entry("이태원1동",  3),
        Map.entry("한남동",     4),
        Map.entry("성수1가1동", 4),
        Map.entry("왕십리2동",  3)
    );

    /**
     * 동 이름으로 경쟁병원 수를 반환한다.
     * Map에 없는 동은 기본값 {@value DEFAULT_COUNT}을 반환한다.
     */
    public int countByDong(String dong) {
        int count = COMPETITOR_MAP.getOrDefault(dong, DEFAULT_COUNT);
        log.debug("[Competitor] dong='{}' → {}", dong, count);
        return count;
    }

    /* ── 심평원 API 연동 (보류) ─────────────────────────────────────────────────
     *
     * 재활성화 시 아래 주석을 해제하고 countByDong() 대신 countCompetitors() 호출.
     * application.yaml: hira.api.key: ${HIRA_API_KEY}
     * AppConfig에 RestTemplate 빈 등록 필요.
     *
     * private static final String API_URL =
     *     "http://apis.data.go.kr/B551182/hospInfoService1/getHospBasisList1";
     * private static final int RADIUS_METER = 2000;
     * private static final int NUM_OF_ROWS  = 100;
     * private static final String DEPT_CODE = "01";
     *
     * @Autowired private RestTemplate restTemplate;
     * @Value("${hira.api.key}") private String apiKey;
     *
     * public int countCompetitors(double lat, double lng) {
     *     try {
     *         URI uri = UriComponentsBuilder.fromHttpUrl(API_URL)
     *                 .queryParam("ServiceKey", apiKey)
     *                 .queryParam("pageNo", 1)
     *                 .queryParam("numOfRows", NUM_OF_ROWS)
     *                 .queryParam("dgsbjtCd", DEPT_CODE)
     *                 .queryParam("xPos", lng)
     *                 .queryParam("yPos", lat)
     *                 .queryParam("radius", RADIUS_METER)
     *                 .build().encode().toUri();
     *         log.info("[HIRA] 요청 URL: {}", uri);
     *         String xml = restTemplate.getForObject(uri, String.class);
     *         return parseCompetitorCount(xml);
     *     } catch (HttpServerErrorException e) {
     *         log.error("[HIRA] 서버 오류 HTTP {} (lat={}, lng={}) — 응답 body:\n{}",
     *                 e.getStatusCode().value(), lat, lng, e.getResponseBodyAsString(), e);
     *         return -1;
     *     } catch (Exception e) {
     *         log.error("[HIRA] API 호출 실패 (lat={}, lng={})", lat, lng, e);
     *         return -1;
     *     }
     * }
     *
     * private int parseCompetitorCount(String xml) throws Exception {
     *     DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
     *     Document doc = builder.parse(new InputSource(new StringReader(xml)));
     *     NodeList resultCodeNodes = doc.getElementsByTagName("resultCode");
     *     if (resultCodeNodes.getLength() > 0) {
     *         String code = resultCodeNodes.item(0).getTextContent().trim();
     *         if (!"00".equals(code)) {
     *             NodeList resultMsgNodes = doc.getElementsByTagName("resultMsg");
     *             String msg = resultMsgNodes.getLength() > 0
     *                     ? resultMsgNodes.item(0).getTextContent().trim() : "(resultMsg 없음)";
     *             log.error("[HIRA] 오류 응답 — resultCode={}, resultMsg={}", code, msg);
     *             return -1;
     *         }
     *     }
     *     NodeList totalCountNodes = doc.getElementsByTagName("totalCount");
     *     if (totalCountNodes.getLength() > 0) {
     *         String raw = totalCountNodes.item(0).getTextContent().trim();
     *         if (!raw.isEmpty()) {
     *             int count = Integer.parseInt(raw);
     *             log.info("[HIRA] 응답 totalCount: {}", count);
     *             return count;
     *         }
     *     }
     *     int itemCount = doc.getElementsByTagName("item").getLength();
     *     log.info("[HIRA] totalCount 없음 — item 개수로 대체: {}", itemCount);
     *     return itemCount;
     * }
     * ────────────────────────────────────────────────────────────────────────── */
}
