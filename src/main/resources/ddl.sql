CREATE DATABASE IF NOT EXISTS histour DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE histour;

CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    email           VARCHAR(50) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    nickname        VARCHAR(255) NOT NULL,
    preferred_lang  VARCHAR(255) DEFAULT 'KO',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_login_at   DATETIME
);

CREATE TABLE trips (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    title       VARCHAR(255),
    trip_date   DATE,
    status      ENUM('IN_PROGRESS','COMPLETED') DEFAULT 'IN_PROGRESS',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE heritage (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    name_hanja      VARCHAR(100),
    category        VARCHAR(100),
    period          ENUM('PREHISTORIC','GOJOSEON','THREE_KINGDOMS','UNIFIED','GORYEO','JOSEON','OPENING','JAPANESE','MODERN','UNKNOWN'),
    location        POINT NOT NULL SRID 4326,
    thumbnail_url   VARCHAR(255),
    ccba_kdcd       VARCHAR(10) NOT NULL,
    ccba_asno       VARCHAR(20) NOT NULL,
    ccba_ctcd       VARCHAR(10) NOT NULL,
    UNIQUE KEY uq_heritage_code (ccba_kdcd, ccba_asno, ccba_ctcd),
    SPATIAL INDEX idx_location (location)
);

CREATE TABLE heritage_descriptions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    heritage_id BIGINT NOT NULL,
    content     TEXT NOT NULL,
    depth_level INT NOT NULL DEFAULT 1,
    topic       VARCHAR(255),
    parent_id   BIGINT,
    source      ENUM('OFFICIAL','AI_GENERATED') NOT NULL,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (heritage_id) REFERENCES heritage(id),
    FOREIGN KEY (parent_id) REFERENCES heritage_descriptions(id)
);

CREATE TABLE heritage_media (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    heritage_id BIGINT NOT NULL,
    url         VARCHAR(1000),
    caption     VARCHAR(255),
    source      VARCHAR(255),
    FOREIGN KEY (heritage_id) REFERENCES heritage(id)
);

CREATE TABLE visit_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id     BIGINT NOT NULL,
    heritage_id BIGINT NOT NULL,
    photo_url   VARCHAR(255),
    location    POINT NOT NULL SRID 4326,
    explanation TEXT,
    visited_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (trip_id) REFERENCES trips(id),
    FOREIGN KEY (heritage_id) REFERENCES heritage(id),
    SPATIAL INDEX idx_visit_location (location)
);

CREATE TABLE quiz (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    heritage_id     BIGINT NOT NULL,
    title           VARCHAR(255) NOT NULL,
    content         VARCHAR(1000) NOT NULL,
    correct_answer  VARCHAR(1000) NOT NULL,
    FOREIGN KEY (heritage_id) REFERENCES heritage(id)
);

CREATE TABLE quiz_choices (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    quiz_id    BIGINT NOT NULL,
    content    VARCHAR(1000) NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (quiz_id) REFERENCES quiz(id)
);

CREATE TABLE quiz_results (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    trip_id         BIGINT NOT NULL,
    quiz_id         BIGINT NOT NULL,
    user_answer     VARCHAR(1000),
    is_correct      BOOLEAN,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (trip_id) REFERENCES trips(id),
    FOREIGN KEY (quiz_id) REFERENCES quiz(id)
);
