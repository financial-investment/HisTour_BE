package com.histour.domain.auth.jwt;

import com.histour.domain.user.dto.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JwtProviderTest {

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    void 토큰_생성테스트(){
        User user = User.builder().
                nickname("최열음")
                .email("xx@email.com")
                .id(1L)
                .build();

        String accessToken = jwtProvider.createAccessToken(1L);
        System.out.println(accessToken);

    }

    @Test
    void 토큰_검증테스트(){








    }
}