package com.histour.domain.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtProvider {
    private final long accessExpMin;
    private final long refreshExpMin;
    private final SecretKey key;

    public JwtProvider(@Value("${jwt.access-token-expiry}") long accessExpMin,
                       @Value("${jwt.refresh-token-expiry}") long refreshExpMin,
                       @Value("${jwt.secret}") String secretKeyString) {
        this.accessExpMin = accessExpMin;
        this.refreshExpMin = refreshExpMin;
        this.key = Keys.hmacShaKeyFor(secretKeyString.getBytes());
    }

    /**
     * Access Token 생성
     */
    public String createAccessToken(Long userId) {
        return createToken(userId, accessExpMin);
    }

    /**
     * Refresh Token 생성
     */
    public String createRefreshToken(Long userId) {
        return createToken(userId, refreshExpMin);
    }

    private String createToken(Long userId, long expiration) {

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * 토큰 검증
     */
    public boolean validateToken(String token) {
        try {

            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * userId 추출
     */
    public Long getUserId(String token) {

        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.parseLong(claims.getSubject());
    }



}
