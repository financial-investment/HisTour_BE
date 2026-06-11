# 여기에선 무슨 일이? — Backend

역사 현장에서 사진 한 장으로 AI 역사 해설을 받고, 여행 후 퀴즈로 기억을 남기는 역사 여행 기록 서비스

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.1 |
| ORM | MyBatis |
| DB | MySQL 8.x |
| Cache | Redis |
| Security | Spring Security + JWT |
| Build | Maven |

---

## 프로젝트 구조

```
src/main/java/com/histour/
├── api/controller/       # REST 컨트롤러
├── batch/                # 국가유산청 API 배치 적재
├── client/               # 외부 API 클라이언트
├── config/               # Security, Redis, Web(CORS) 설정
├── common/               # 공통 응답, 예외 처리
└── domain/
    ├── user/             # 사용자
    ├── heritage/         # 문화재 + 이벤트
    ├── trip/             # 여행 + 방문 로그
    ├── quiz/             # 퀴즈 + 결과
    └── report/           # 여행 리포트
```

---

## 환경 설정

### 필수 환경변수

```
DB_USERNAME       MySQL 사용자명 (기본값: root)
DB_PASSWORD       MySQL 비밀번호 (기본값: 1234)
REDIS_HOST        Redis 호스트  (기본값: localhost)
JWT_SECRET        JWT 서명 키
```

> 로컬 개발 시 환경변수 미설정이면 기본값으로 동작합니다.

### DB 생성

```sql
CREATE DATABASE heritage_trip CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

## 실행 방법

```bash
# 빌드
./mvnw clean install

# 실행
./mvnw spring-boot:run
```

서버 기본 포트: `http://localhost:8080`

---

## API 엔드포인트 (예정)

| Method | URL | 설명 |
|---|---|---|
| POST | `/api/heritage/explain` | 사진 + 위치 → 역사 해설 |
| GET | `/api/trip` | 여행 목록 조회 |
| POST | `/api/trip` | 여행 생성 |
| GET | `/api/quiz/generate` | 퀴즈 생성 |
| POST | `/api/quiz/result` | 퀴즈 결과 저장 |
| GET | `/api/report/{tripId}` | 여행 리포트 조회 |
