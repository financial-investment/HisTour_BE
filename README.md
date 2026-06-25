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
| Cache / Vector | Redis Stack (Refresh Token + KNN 벡터 검색) |
| Security | Spring Security + JWT (jjwt 0.12.3) |
| AI | GMS API (OpenAI 호환 gpt-4o / Anthropic 호환 claude-haiku / text-embedding-3-small) |
| Docs | SpringDoc OpenAPI 2.x (Swagger UI) |
| Build | Maven |
| 인프라 | Docker Compose (MySQL + Redis Stack) |

---

## 프로젝트 구조

```
src/main/java/com/histour/
├── HistourApplication.java
├── api/controller/
│   ├── HeritageController.java   # 문화재 조회·해설 API
│   ├── UserController.java       # 회원가입/조회
│   ├── TripController.java       # 여행 관리 + 추천
│   ├── QuizController.java       # 퀴즈 생성/채점
│   └── ReportController.java     # 리포트
├── batch/
│   ├── HeritageDataLoader.java   # 국가유산청 데이터 1회성 적재 (enabled: false)
│   └── EmbeddingLoader.java      # heritage 전체 임베딩 → Redis Stack 적재 (enabled: false)
├── client/
│   ├── GmsAiClient.java          # GMS AI 호출 전담 (gpt-4o + Claude Haiku + Embeddings + 퀴즈 생성)
│   └── HeritageApiClient.java    # 국가유산청 Open API 클라이언트
├── config/
│   ├── SecurityConfig.java       # JWT 필터, /api/auth/** · /api/user(POST) · swagger 제외 인증 필요
│   ├── RedisConfig.java          # StringRedisTemplate + JedisPooled 빈 등록
│   ├── WebConfig.java            # CORS
│   └── SwaggerConfig.java
├── domain/
│   ├── auth/                     # JWT 로그인/갱신/로그아웃
│   ├── heritage/                 # 문화재 도메인
│   ├── trip/                     # 여행·방문기록 도메인
│   ├── user/                     # 사용자 도메인
│   ├── quiz/                     # 여행 후 퀴즈 도메인
│   └── report/                   # 추천·리포트 도메인
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

### Docker Compose로 로컬 환경 실행 (권장)

Redis Stack(벡터 검색 모듈 포함)이 필요하므로 일반 Redis는 사용 불가합니다.

```bash
# MySQL + Redis Stack 컨테이너 실행
DB_PASSWORD=1234 docker compose up -d

# 종료
docker compose down
```

### DB 초기화

```sql
CREATE DATABASE histour CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

이후 `src/main/resources/ddl.sql`을 실행하여 테이블을 생성합니다.

### 문화재 데이터 적재

국가유산청 Open API에서 데이터를 받아 적재하는 1회성 로더입니다.
전국 8,314건+ 적재 완료 상태이므로 DB를 덤프에서 복원한 경우 재실행 불필요.

```yaml
# application.yaml
heritage:
  loader:
    enabled: true   # 적재 후 반드시 false로 되돌릴 것
```

### 임베딩 배치 실행

추천·리포트 API 사용 전 heritage 전체 임베딩을 Redis Stack에 적재해야 합니다.
8,314건 기준 약 50분 소요 (GMS API text-embedding-3-small, ~9 크레딧).
이미 적재 완료된 Redis를 사용하는 경우 재실행 불필요.

```yaml
# application.yaml
embedding:
  loader:
    enabled: true   # 적재 후 반드시 false로 되돌릴 것
```

---

## 실행 방법

```bash
# 빌드
./mvnw clean install -DskipTests

# 실행 (로컬 프로파일 사용 권장)
DB_PASSWORD=1234 ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

- 서버 기본 포트: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Swagger UI 우측 상단 `Authorize`에서 access token을 입력하면 JWT 인증 API를 호출할 수 있습니다. `Bearer` 접두사는 입력하지 않아도 됩니다.

---

## API 엔드포인트

| Method | URL | 설명 |
|---|---|---|
| POST | `/api/auth/login` | 로그인 → Access/Refresh Token 발급 |
| POST | `/api/auth/refresh` | Access Token 갱신 |
| POST | `/api/auth/logout` | 로그아웃 (Refresh Token 폐기) |
| POST | `/api/user` | 회원가입 |
| GET | `/api/user/me` | 내 정보 조회 |
| GET | `/api/heritage/{heritageId}` | 문화재 상세 조회 |
| GET | `/api/heritage/map` | 지도 시야(bounding box) 내 문화재 목록 (최대 100건) |
| POST | `/api/heritage/explain` | 사진 + 위치 → 문화재 식별 + AI 해설 |
| GET | `/api/heritage/{heritageId}/explain/deeper` | 심화 해설 (visitLogId 필수) |
| GET | `/api/trip` | 내 여행 목록 (최신순, visitCount 포함) |
| POST | `/api/trip` | 여행 생성 (IN_PROGRESS 여행 중복 불가) |
| GET | `/api/trip/{tripId}` | 여행 상세 + 방문 기록 목록 |
| PATCH | `/api/trip/{tripId}/complete` | 여행 완료 처리 |
| GET | `/api/trip/{tripId}/recommend/next` | 진행 중 다음 문화재 추천 (KNN + 거리 필터) |
| GET | `/api/report/{tripId}` | 여행 리포트 + 완료 후 코스 추천 |
| POST | `/api/quiz/sessions` | 방문 기록 기반 퀴즈 10개 생성/조회 |
| GET | `/api/quiz/sessions?tripId={tripId}` | 생성된 퀴즈 세션 조회 |
| POST | `/api/quiz/results` | 퀴즈 답안 제출 및 채점 결과 저장 |
| GET | `/api/quiz/results?tripId={tripId}` | 저장된 퀴즈 채점 결과 조회 |

---

### 문화재 지도 API

**`GET /api/heritage/map`**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `swLat` | double | Y | 남서쪽 위도 |
| `swLng` | double | Y | 남서쪽 경도 |
| `neLat` | double | Y | 북동쪽 위도 |
| `neLng` | double | Y | 북동쪽 경도 |

```json
// Response
{
  "success": true,
  "data": [
    {
      "heritageId": 208,
      "name": "경복궁 근정전",
      "lat": 37.5783,
      "lng": 126.9770,
      "thumbnailUrl": "https://..."
    }
  ]
}
```

- 카카오맵 `getBounds()` → SW·NE 좌표로 호출
- 최대 100건 반환 (기존 SPATIAL INDEX 활용)
- 인증 필요

---

### 해설 API

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

- 기본 해설의 `visitLogId`를 파라미터로 전달
- 동일 문화재에 대한 심화 해설은 DB에 캐싱 (depth_level=2)

---

### 추천 API

**`GET /api/trip/{tripId}/recommend/next`**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `lat` | double | Y | 현재 위도 |
| `lng` | double | Y | 현재 경도 |
| `radiusKm` | double | N | 탐색 반경 km (기본값: 5) |

- 방문 기록 있을 때: 방문 문화재 임베딩 평균 → KNN 100개 → 반경 내 미방문 필터 → 거리순 5개
- 방문 기록 없을 때: 좌표 기반 근거리 5개 반환 (폴백)

---

### 퀴즈 API

여행 완료 후 `tripId`를 전달하면 방문 유적지 기반 10문제를 구성합니다.
기존 DB 문제가 부족하면 GMS Claude API로 부족분을 생성·저장합니다.

**`POST /api/quiz/sessions`** → 이미 생성된 세션이 있으면 기존 세션 반환 (멱등)

**`POST /api/quiz/results`** → 전체 답안 일괄 제출·채점, 중복 제출 불가

---

### 리포트 API

**`GET /api/report/{tripId}`**

- 방문 문화재 기반 KNN → 다른 지역 문화재 코스 추천
- GPT-4o로 여행 요약 생성
- 임베딩 배치 적재 완료 후 사용 가능

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
| 201 | 리소스 생성 완료 |
| 400 | 요청 오류 (문화재 식별 불가, 중복 여행 등) |
| 401 | 인증 실패 (토큰 없음 또는 만료) |
| 403 | 소유하지 않은 여행/퀴즈 접근 |
| 404 | 리소스 없음 |
| 502 | GMS AI API 호출 실패 |
| 500 | 서버 내부 오류 |
