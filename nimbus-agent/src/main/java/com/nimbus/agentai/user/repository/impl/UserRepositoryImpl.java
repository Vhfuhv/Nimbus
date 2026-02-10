package com.nimbus.agentai.user.repository.impl;

import com.nimbus.agentai.user.mapper.UserMapper;
import com.nimbus.agentai.user.model.UserEntity;
import com.nimbus.agentai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;

    @Override
    public Optional<UserEntity> findByUserKey(String userKey) {
        return Optional.ofNullable(userMapper.findByUserKey(userKey));
    }

    @Override
    public UserEntity save(UserEntity user) {
        userMapper.insert(user);
        return user;
    }
}

