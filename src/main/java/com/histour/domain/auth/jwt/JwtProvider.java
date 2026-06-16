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
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

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
        return createToken(userId, accessExpMin, ACCESS_TOKEN_TYPE);
    }

    /**
     * Refresh Token 생성
     */
    public String createRefreshToken(Long userId) {
        return createToken(userId, refreshExpMin, REFRESH_TOKEN_TYPE);
    }

    private String createToken(Long userId, long expiration, String tokenType) {

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TOKEN_TYPE_CLAIM, tokenType)
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

            parseClaims(token);

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * userId 추출
     */
    public Long getUserId(String token) {

        Claims claims = parseClaims(token);

        return Long.parseLong(claims.getSubject());
    }

    public boolean isAccessToken(String token) {
        return ACCESS_TOKEN_TYPE.equals(getTokenType(token));
    }

    public boolean isRefreshToken(String token) {
        return REFRESH_TOKEN_TYPE.equals(getTokenType(token));
    }

    private String getTokenType(String token) {
        return parseClaims(token).get(TOKEN_TYPE_CLAIM, String.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }


}
