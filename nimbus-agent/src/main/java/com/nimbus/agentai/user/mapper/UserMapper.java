package com.nimbus.agentai.user.mapper;

import com.nimbus.agentai.user.model.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    UserEntity findByUserKey(@Param("userKey") String userKey);

    int insert(UserEntity user);
}

