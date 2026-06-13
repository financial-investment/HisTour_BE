package com.histour.domain.user.mapper;

import com.histour.domain.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {

    void save(User user);

    User findById(Long id);

    User findByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByEmail(String email);
}
