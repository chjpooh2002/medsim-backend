# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MEDSIM is a medical clinic financial simulation platform (의료 개원 시뮬레이션). It calculates 36-month P&L and cash flow projections for opening a clinic based on department, location, staffing, and financing parameters.

## Commands

All commands run from the `backend/` directory:

```bash
./gradlew bootRun        # Start dev server on localhost:8080
./gradlew build          # Compile + run tests
./gradlew test           # Run tests only
./gradlew bootJar        # Build executable JAR
./gradlew clean          # Delete build output
```

Run a single test class:
```bash
./gradlew test --tests "com.medsim.backend.BackendApplicationTests"
```

API docs (Swagger UI) available at `http://localhost:8080/swagger-ui.html` when running.

## Architecture

```
backend/src/main/java/com/medsim/backend/
├── BackendApplication.java          # Spring Boot entry point
├── controller/
│   ├── SimulationController.java    # POST /api/simulation
│   └── HealthCheckController.java   # GET /health
├── service/
│   └── SimulationService.java       # All simulation logic
├── dto/
│   ├── request/SimulationRequest.java  # Input: department, region, staff, loan
│   └── response/SimulationResult.java  # Output: KPIs + 36 MonthlyData rows
└── domain/
    └── MonthlyData.java             # Per-month P&L + cash flow record
```

Static frontend lives in `backend/src/main/resources/static/` (vanilla HTML/JS).

### Simulation Logic (SimulationService)

Core algorithm for 36-month projection:

- **Patient growth**: S-curve model — ramp factor `0.3 + month * 0.03`, capped so it stabilizes
- **Revenue**: `basePatientsPerDay × revenuePerPatient × workingDays(22) × areaFactor × rampFactor`
- **Variable cost**: 15% of revenue; other management: 2% of revenue
- **Fixed costs**: staff salaries + 10.6% 4대보험 + rent + marketing + depreciation
- **Depreciation**: straight-line over 60 months (`initialInvestment / 60`)
- **Loan repayment**: principal-equal monthly (declining interest = `remainingPrincipal × monthlyRate`)
- **Tax**: 20% effective rate on operating profit > 0 only (no loss carryforward)
- **Cash flow**: starts at `initialInvestment` (equity portion), adds net profit, subtracts principal + interest

Specialty base data (patients/day, revenue/patient) and area factors for Seoul regions are hard-coded constants in `SimulationService` — these are **planned for DB replacement**.

Area factor groups:
- 1.0x: 강남, 서초, 송파
- 0.78x: default (other areas)
- 0.6x: 노원, 도봉, 중랑

### KPI Messages (12 categories)

Output includes contextual alerts generated from simulation results:
- BEP timing: month ≤6 (success), ≤12 (warning), >12 (danger)
- Fixed cost ratio thresholds: ≤40%, ≤59%, >59%
- Final cash balance vs. 3-month operating expense reserve
- Patient growth rate: ≥5%, ≥0%, negative

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.5.13 |
| Build | Gradle 8.14.4 |
| API Docs | SpringDoc OpenAPI 2.3.0 |
| Code Gen | Lombok (`@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor`) |
| Testing | JUnit 5 |
| Database | MySQL (dependency present; **JPA/DataSource auto-config is currently excluded** in `application.yaml`) |
| Frontend | Static HTML/CSS/JS |

## Commit Rules

- 커밋 메시지에 Co-Authored-By, co-authored-by, Claude 관련 내용 절대 포함하지 말 것
- 커밋 메시지는 `feat` / `fix` / `refactor` / `chore` / `build` / `docs` 타입만 사용
- 커밋 전 항상 `./gradlew build` 로 빌드 확인

## Key Constraints

- **No DB yet**: `DataSourceAutoConfiguration` and `HibernateJpaAutoConfiguration` are excluded in `application.yaml`. Domain entities and JPA repositories are not wired. Do not enable them until a DB is configured.
- **Hard-coded specialty/area data**: `SimulationService` contains hard-coded maps for department base rates and region factors. When replacing with DB, these are the values to migrate.
- **Single module**: only one Gradle subproject (`backend/`); `settings.gradle` root is named `backend`.
