package com.nimbus.agentai.user.repository;

import com.nimbus.agentai.user.model.UserEntity;

import java.util.Optional;

public interface UserRepository {

    Optional<UserEntity> findByUserKey(String userKey);

    UserEntity save(UserEntity user);
}

