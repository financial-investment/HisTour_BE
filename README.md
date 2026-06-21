# HisTour — Backend

역사 현장에서 사진 한 장으로 AI 역사 해설을 받고, 여행 후 퀴즈로 기억을 남기는 역사 여행 기록 서비스

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.1 |
| ORM | MyBatis |
| DB | MySQL 8.x (`histour`) |
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
│   ├── TripController.java       # 여행 관리 (구현 완료)
│   ├── QuizController.java       # 여행 후 퀴즈 생성/채점 (구현 완료)
│   └── ReportController.java     # 리포트 (미구현)
├── batch/
│   └── HeritageDataLoader.java   # 국가유산청 데이터 1회성 적재 (enabled: false)
├── client/
│   ├── GmsAiClient.java          # GMS AI 호출 (gpt-4o + Claude Haiku)
│   ├── QuizAiClient.java         # 퀴즈 생성용 GMS Claude 호출
│   └── HeritageApiClient.java    # 국가유산청 Open API 클라이언트
├── config/
│   ├── SecurityConfig.java       # JWT 필터, /api/auth/** · /api/user(POST) · swagger 제외 인증 필요
│   ├── RedisConfig.java
│   ├── WebConfig.java            # CORS
│   └── SwaggerConfig.java
├── domain/
│   ├── auth/                     # JWT 로그인/갱신/로그아웃 (구현 완료)
│   ├── heritage/                 # 문화재 도메인 (구현 완료)
│   ├── trip/                     # 여행·방문기록 도메인 (구현 완료)
│   ├── user/                     # 사용자 도메인 (구현 완료)
│   ├── quiz/                     # 여행 후 퀴즈 도메인 (구현 완료)
│   └── report/                   # 리포트 (미구현)
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
CREATE DATABASE histour CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
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
- Swagger UI 우측 상단 `Authorize`에서 access token을 입력하면 JWT 인증 API를 호출할 수 있습니다. `Bearer` 접두사는 입력하지 않아도 됩니다.

---

## API 엔드포인트

### 구현 완료

| Method | URL | 설명 |
|---|---|---|
| POST | `/api/auth/login` | 로그인 → Access/Refresh Token 발급 |
| POST | `/api/auth/refresh` | Access Token 갱신 |
| POST | `/api/auth/logout` | 로그아웃 (Refresh Token 폐기) |
| POST | `/api/user` | 회원가입 |
| GET | `/api/user/me` | 내 정보 조회 |
| POST | `/api/heritage/explain` | 사진 + 위치 → 문화재 식별 + AI 해설 |
| GET | `/api/heritage/{heritageId}/explain/deeper` | 심화 해설 (visitLogId 필수) |
| GET | `/api/trip` | 내 여행 목록 (최신순, visitCount 포함) |
| POST | `/api/trip` | 여행 생성 (IN_PROGRESS 여행 중복 불가) |
| GET | `/api/trip/{tripId}` | 여행 상세 + 방문 기록 목록 |
| PATCH | `/api/trip/{tripId}/complete` | 여행 완료 처리 |
| POST | `/api/quiz/sessions` | 여행 방문 기록 기반 퀴즈 10개 생성/조회 |
| GET | `/api/quiz/sessions?tripId={tripId}` | 생성된 퀴즈 세션 조회 |
| POST | `/api/quiz/results` | 퀴즈 답안 제출 및 채점 결과 저장 |

### 미구현

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/trip/{tripId}/recommend/next` | 진행 중 다음 문화재 추천 |
| GET | `/api/report/{tripId}` | 여행 리포트 + 완료 후 추천 |

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

### Trip API 상세

**여행 생성** `POST /api/trip`

```json
// Request
{ "title": "경복궁 탐방", "tripDate": "2026-06-17" }

// Response  201 Created
{ "success": true, "data": 1 }
```

- `title`, `tripDate` 모두 nullable
- 이미 IN_PROGRESS 여행이 있으면 `400` 반환

**내 여행 목록** `GET /api/trip`

```json
// Response
{
  "success": true,
  "data": [
    {
      "tripId": 1,
      "title": "경복궁 탐방",
      "tripDate": "2026-06-17",
      "status": "IN_PROGRESS",
      "createdAt": "2026-06-17T10:00:00",
      "visitCount": 3
    }
  ]
}
```

**여행 상세** `GET /api/trip/{tripId}`

```json
// Response
{
  "success": true,
  "data": {
    "tripId": 1,
    "title": "경복궁 탐방",
    "status": "COMPLETED",
    "visitCount": 2,
    "visitLogs": [
      {
        "id": 5,
        "heritageId": 1,
        "heritageName": "경복궁",
        "explanation": "지금 당신이 서 있는 이 곳에서는...",
        "visitedAt": "2026-06-17T11:30:00"
      }
    ]
  }
}
```

- 본인 여행이 아니면 `400` 반환

**여행 완료** `PATCH /api/trip/{tripId}/complete`

- 이미 COMPLETED 상태면 `400` 반환
- 클라이언트는 여행 완료 성공 후 `POST /api/quiz/sessions`에 같은 `tripId`를 전달해 오늘의 퀴즈를 생성합니다.

---

### Quiz API 상세

여행 완료 후 클라이언트가 `tripId`를 전달하면, 서버는 해당 여행의 `visit_logs`를 기준으로 방문 유적지와 연결된 문제를 10개 구성합니다. 기존 `quiz` 문제가 부족하면 `QuizAiClient`가 GMS Claude API를 호출해 부족분을 생성하고, 생성된 문제와 객관식 선택지를 각각 `quiz`, `quiz_choices`에 저장합니다.

**퀴즈 세션 생성** `POST /api/quiz/sessions`

```json
// Request
{
  "tripId": 1
}
```

```json
// Response
{
  "success": true,
  "data": {
    "tripId": 1,
    "totalCount": 10,
    "questions": [
      {
        "sessionId": 10,
        "quizId": 100,
        "heritageId": 1,
        "heritageName": "서울 숭례문",
        "title": "숭례문 복습",
        "content": "다음 중 숭례문에 대한 설명으로 옳은 것은?",
        "source": "AI_GENERATED",
        "difficulty": "MEDIUM",
        "sortOrder": 1,
        "choices": [
          { "choiceId": 1, "content": "조선 시대 도성의 남쪽 문이다." },
          { "choiceId": 2, "content": "고려 시대 왕궁의 정문이다." }
        ]
      }
    ]
  }
}
```

- 이미 생성된 세션이 있으면 새로 만들지 않고 기존 세션을 반환합니다.
- 문제 후보는 방문 유적지별로 균형 있게 랜덤 선택합니다.
- 응답에는 정답 여부와 해설을 포함하지 않습니다.
- AI 호출은 DB 트랜잭션 밖에서 수행하고, `quiz`, `quiz_choices`, `quiz_sessions` 저장만 짧은 트랜잭션으로 처리합니다.

**퀴즈 세션 조회** `GET /api/quiz/sessions?tripId={tripId}`

- 이미 생성된 퀴즈 문제와 선택지를 다시 조회합니다.
- 본인 소유의 여행이 아니면 `403` 반환.

**퀴즈 답안 제출** `POST /api/quiz/results`

```json
// Request
{
  "answers": [
    { "sessionId": 10, "choiceId": 1 },
    { "sessionId": 11, "choiceId": 5 }
  ]
}
```

```json
// Response
{
  "success": true,
  "data": {
    "tripId": 1,
    "totalCount": 10,
    "correctCount": 8,
    "accuracy": 80,
    "results": [
      {
        "sessionId": 10,
        "quizId": 100,
        "correct": true,
        "selectedChoiceId": 1,
        "correctChoiceId": 1,
        "explanation": "숭례문은 조선 한양도성의 남대문입니다."
      }
    ]
  }
}
```

- `choiceId`가 해당 문제의 선택지인지 검증합니다.
- 이미 제출된 세션은 중복 제출할 수 없습니다.
- 채점 결과는 `quiz_results`에 저장하고, `quiz_sessions.status`를 `SUBMITTED`로 변경합니다.

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
| 201 | 리소스 생성 완료 (여행 생성 등) |
| 400 | 요청 오류 (문화재 식별 불가, 기본 해설 없음, 중복 여행, 권한 없음 등) |
| 403 | 소유하지 않은 여행/퀴즈 접근 |
| 401 | 인증 실패 (토큰 없음 또는 만료) |
| 404 | 리소스 없음 (반경 내 문화재 없음, ID 미존재 등) |
| 502 | GMS AI API 호출 실패 |
| 500 | 서버 내부 오류 |
