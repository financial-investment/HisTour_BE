# HisTour — Backend

역사 현장에서 사진 한 장으로 AI 역사 해설을 받고, 여행 후 퀴즈로 기억을 남기는 역사 여행 기록 서비스

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.1 |
| ORM | MyBatis |
| DB | MySQL 8.x (`heritage_trip`) |
| Cache | Redis (Refresh Token 전용) |
| Security | Spring Security + JWT (jjwt 0.12.3) |
| AI | GMS API (OpenAI 호환 gpt-4o / Anthropic 호환 claude-haiku) |
| Docs | SpringDoc OpenAPI 2.x (Swagger UI) |
| Build | Maven |

---

## 프로젝트 구조

```
src/main/java/com/histour/
├── HistourApplication.java
├── api/controller/
│   ├── HeritageController.java   # 문화재 해설 API (구현 완료)
│   ├── UserController.java       # 회원가입/조회 (구현 완료)
│   ├── TripController.java       # 여행 관리 (미구현)
│   ├── QuizController.java       # 퀴즈 (미구현)
│   └── ReportController.java     # 리포트 (미구현)
├── batch/
│   └── HeritageDataLoader.java   # 국가유산청 데이터 1회성 적재 (enabled: false)
├── client/
│   ├── GmsAiClient.java          # GMS AI 호출 (gpt-4o + Claude Haiku)
│   └── HeritageApiClient.java    # 국가유산청 Open API 클라이언트
├── config/
│   ├── SecurityConfig.java       # 임시 전체 permitAll, STATELESS
│   ├── RedisConfig.java
│   ├── WebConfig.java            # CORS
│   └── SwaggerConfig.java
└── common/
    ├── exception/GlobalExceptionHandler.java
    └── response/ApiResponse.java  # { success, data, message }
```

---

## 환경 설정

### 필수 환경변수

| 변수 | 설명 | 기본값 |
|---|---|---|
| `DB_USERNAME` | MySQL 사용자명 | `root` |
| `DB_PASSWORD` | MySQL 비밀번호 | (없음, 필수) |
| `REDIS_HOST` | Redis 호스트 | `localhost` |
| `JWT_SECRET` | JWT 서명 키 (256bit 이상) | 로컬 개발용 기본값 있음 |
| `GMS_API_KEY` | SSAFY GMS API 키 | (없음, 필수) |

### DB 생성

```sql
CREATE DATABASE heritage_trip CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

이후 `src/main/resources/ddl.sql`을 실행하여 테이블을 생성합니다.

### 문화재 데이터 적재

국가유산청 Open API에서 데이터를 받아 적재하는 1회성 로더가 포함되어 있습니다.

```yaml
# application.yaml
heritage:
  loader:
    enabled: true   # 적재 후 반드시 false로 되돌릴 것
```

`enabled: true`로 변경 후 앱을 실행하면 자동 적재됩니다.

---

## 실행 방법

```bash
# 빌드
./mvnw clean install -DskipTests

# 실행
./mvnw spring-boot:run
```

- 서버 기본 포트: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## API 엔드포인트

### 구현 완료

| Method | URL | 설명 |
|---|---|---|
| POST | `/api/user` | 회원가입 |
| GET | `/api/user/{userId}` | 사용자 조회 |
| POST | `/api/heritage/explain` | 사진 + 위치 → 문화재 식별 + AI 해설 |
| GET | `/api/heritage/{heritageId}/explain/deeper` | 심화 해설 (visitLogId 필수) |

### 미구현

| Method | URL | 설명 |
|---|---|---|
| GET/POST | `/api/trip` | 여행 목록 조회 / 생성 |
| GET | `/api/quiz/generate` | 여행 기반 퀴즈 생성 |
| POST | `/api/quiz/result` | 퀴즈 결과 저장 |
| GET | `/api/report/{tripId}` | 여행 리포트 조회 |

---

### 해설 API 상세

**기본 해설** `POST /api/heritage/explain`

```json
// Request
{
  "image": "Base64 인코딩 사진 (data:image/... 프리픽스 포함 가능)",
  "lat": 37.5796,
  "lng": 126.9770,
  "tripId": 1
}

// Response
{
  "success": true,
  "data": {
    "heritageId": 1,
    "heritageName": "창덕궁",
    "explanation": "지금 당신이 서 있는 이 곳에서는...",
    "visitLogId": 5
  }
}
```

- 현재 좌표 반경 500m 내 문화재를 조회하여 AI로 식별
- `tripId` 있으면 방문 기록(`visit_logs`) 저장 → `visitLogId` 반환
- `tripId` 없으면 `visitLogId: null` → 심화 해설 불가

**심화 해설** `GET /api/heritage/{heritageId}/explain/deeper?visitLogId={id}`

```json
// Response
{
  "success": true,
  "data": {
    "heritageId": 1,
    "heritageName": "창덕궁",
    "explanation": "잘 알려지지 않은 비화...",
    "visitLogId": null
  }
}
```

- 기본 해설의 `visitLogId`를 파라미터로 전달
- 동일 문화재에 대한 심화 해설은 DB에 캐싱 (depth_level=2)

---

## 공통 응답 형식

```json
// 성공
{ "success": true, "data": { ... }, "message": null }

// 실패
{ "success": false, "data": null, "message": "오류 메시지" }
```

| HTTP 상태 | 상황 |
|---|---|
| 200 | 정상 처리 |
| 400 | 요청 오류 (식별 불가, 기본 해설 없음 등) |
| 404 | 리소스 없음 (반경 내 문화재 없음, ID 미존재 등) |
| 500 | 서버 내부 오류 |
