# MEDSIM — 의료 개원 시뮬레이션 플랫폼

예비 개원의가 진료과목·지역·인력·자금 조달 조건을 설정하고, 36개월 병원 경영을 턴제로 시뮬레이션하여 **개원 전 재무 리스크를 정량적으로 검증**하는 서비스입니다. 매달 경영 의사결정(마케팅 강화·직원 채용·비용 절감 등)을 선택하고, 6개월 주기로 발생하는 랜덤 이벤트(경쟁 병원 개원·의료 사고·독감 시즌 등)에 대응하면서 36개월 뒤 병원이 살아남을 수 있는지를 확인합니다.

**데모:** https://medsim-v73a.onrender.com/

---

## 🎯 핵심 아키텍처 및 차별화 포인트

### 1. 결정론적 단일 계산 파이프라인 (`runOneTurn`)

모든 재무 수치는 `SimulationService.runOneTurn()` 하나에서 순서대로 산출됩니다. 화면별로 계산 로직이 흩어지면 수치 불일치 버그가 누적되기 때문에, 파이프라인을 단일 메서드로 고정했습니다.

```
① 의사결정 효과 적용        decisionIds → revMultiplier / extraCost / 비재무 지표 델타
② 이벤트 효과 + 대응 옵션   triggerMonth 매칭 → impactMap + 선택한 optionId effectMap 합산
③ 환자수 (S커브)            growth = min(1.0, 0.3 + month × 0.03)
                            patients = basePatientsPerDay × 22일 × areaFactor × growth
④ 매출                      revenue = patients × revenuePerPatient × revMultiplier
⑤ 비용                      변동비 15% + 기타관리비 2% + 고정비(인건비·4대보험·임대료·마케팅·감가상각)
⑥ 영업이익                  revenue - variableCost - fixedCost
⑦ 이자비용                  원금 균등상환 → remainingLoan × (rate/100) / 12
⑧ 세금                      영업이익 > 0 이면 20% (결손 이월 없음)
⑨ 현금흐름 3분류            영업 CF / 투자 CF(1개월차 초기 투자) / 재무 CF(원금 상환)
⑩ 비재무 지표 갱신          staffMorale → satisfactionScore → reputationScore → returnPatientRate
```

### 2. 인메모리 캐시 + MySQL write-through 상태 영속화

턴제 시뮬레이션은 매 턴 `SimulationState`를 읽고 갱신하므로, 매번 DB를 조회하면 응답 레이턴시가 선형으로 증가합니다. 이를 막기 위해 `ConcurrentHashMap`을 L1 캐시로 두고, 상태를 JSON으로 직렬화해 MySQL에 write-through합니다.

```java
// loadState: 캐시 우선 → miss 시 DB 역직렬화 후 캐시에 적재
SimulationState cached = simulationStore.get(simulationId);
if (cached != null) return cached;
return simulationStateRepository.findBySimulationId(simulationId)
        .map(entity -> objectMapper.readValue(entity.getStateJson(), SimulationState.class));

// persistState: 상태 → JSON → simulation_state 테이블 upsert
// DB 저장 실패 시 인메모리 상태는 유효하게 유지
```

`simulation_state` 테이블의 `state_json` 컬럼(`LONGTEXT`)에 전체 상태를 저장하므로 스키마 변경 없이 상태 구조를 확장할 수 있습니다.

### 3. `simulationId` 시드 기반 이벤트 재현성

동일한 `simulationId`라면 언제 재실행해도 동일한 이벤트 배열이 생성됩니다.

```java
long seed = simId.hashCode();
Random rng = new Random(seed);
// 6 / 12 / 18 / 24 / 30 / 36개월 체크포인트에 각 1~2개 이벤트 배정
// 부정 이벤트 비율: 체크포인트별 50~60%
```

이벤트 템플릿은 12종 (MARKET·OPERATION·PATIENT·GROWTH 4카테고리), 각 이벤트마다 대응 옵션 4가지를 보유합니다. 결과 재현이 보장되므로 동일한 의사결정 경로를 재시험할 수 있고, 테스트 코드에서도 시드를 고정해 결과를 단언할 수 있습니다.

### 4. 단일 `MonthlyData`로 모든 화면 정합성 보장

36개월 그리드, 타임라인, 누적 손익 차트, KPI 카드, 운영 리포트가 모두 `List<MonthlyData>`를 단일 소스로 사용합니다. 화면별로 별도 집계 로직을 두지 않으므로, 어떤 화면에서 보더라도 수치가 일치합니다. `MonthlyData`는 재무 지표(매출·비용·영업이익·순이익·현금흐름 3분류)와 비재무 지표(평판·만족도·재진율·직원 사기)를 모두 포함합니다.

---

## 🛠️ 사용 기술 및 환경

| 분류 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.13 |
| ORM | Spring Data JPA (Hibernate) |
| Security | Spring Security + JJWT 0.12.6 (JWT Bearer) |
| Validation | Spring Boot Validation (Bean Validation) |
| Build | Gradle |
| API Docs | SpringDoc OpenAPI 2.8.9 (Swagger UI) |
| Code Gen | Lombok |
| Database | MySQL (prod) / H2 in-memory (local·test) |
| Serialization | Jackson ObjectMapper (상태 JSON 직렬화) |
| Testing | JUnit 5, Mockito |
| External API | HIRA API (경쟁 병원 수 조회) |
| Frontend | Static HTML/CSS/JS (`src/main/resources/static/`) |

---

## 📊 도메인 모델 관계

```
members (회원)
  id          PK
  email       UNIQUE
  name
  role        USER | ADMIN
  created_at

simulation_histories (시뮬레이션 이력)
  id          PK
  member_id   FK → members.id
  dept_category
  region_si_gun
  bep_month
  fixed_cost_ratio
  final_cash_balance
  request_json    TEXT  (SimulationRequest 직렬화)
  result_json     TEXT  (SimulationResult 직렬화)
  created_at

simulation_state (턴제 진행 상태)
  simulation_id   PK  (UUID)
  member_email
  state_json      LONGTEXT  (SimulationState 전체 직렬화)
  current_month
  is_completed
  is_bankrupt
  created_at / updated_at
```

## 📡 주요 API

### 인증 (`/api/auth`)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/auth/signup` | 회원가입 → JWT 반환 |
| POST | `/api/auth/login` | 로그인 → JWT 반환 |

### 시뮬레이션 (`/api/simulation`)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/simulation` | 36개월 일괄 시뮬레이션 (비로그인 가능) |
| GET | `/api/simulation/mock` | 강남구·내과 기준 하드코딩 결과 (프론트 개발용) |
| POST | `/api/simulation/start` | 턴제 시작 → `simulationId` + 1개월차 결과 반환 |
| POST | `/api/simulation/{id}/next` | `decisionIds` 제출 → 다음 달 실행, 완료·파산 시 `finalResult` 포함 |
| POST | `/api/simulation/{id}/event-response` | 이벤트 대응 옵션 선택 (다음 턴에 효과 반영) |

### 시뮬레이션 이력 — JWT 필요 (`/api/history`)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/history` | 시뮬레이션 실행 + 회원 이력 저장 |
| GET | `/api/history` | 내 이력 목록 (최신순) |
| GET | `/api/history/{id}` | 이력 상세 조회 |
| DELETE | `/api/history/{id}` | 이력 삭제 |

### 입지 분석 / 임대료

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/location/analyze` | 동 선택 시 임대료·유동인구·경쟁 병원 수·입지점수·리스크 레벨 반환 (HIRA API 연동) |
| GET | `/api/region/rent` | 구·진료과목별 평균 임대료 조회 |

Swagger UI: `http://localhost:8080/swagger-ui.html`

---


