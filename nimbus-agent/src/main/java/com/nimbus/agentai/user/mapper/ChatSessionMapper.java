package com.nimbus.agentai.user.mapper;

import com.nimbus.agentai.user.model.ChatSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatSessionMapper {

    ChatSessionEntity findByUserIdAndSessionId(@Param("userId") Long userId,
                                               @Param("sessionId") String sessionId);

    List<ChatSessionEntity> findByUserId(@Param("userId") Long userId);

    int insert(ChatSessionEntity chatSession);

    int touch(@Param("userId") Long userId,
              @Param("sessionId") String sessionId);
}
