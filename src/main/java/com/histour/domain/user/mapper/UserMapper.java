package com.histour.domain.user.mapper;

import com.histour.domain.user.dto.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {

    void save(User user);

    User findById(Long id);

    User findByEmail(String email);

    int existsByNickname(String nickname);

    int existsByEmail(String email);
}
