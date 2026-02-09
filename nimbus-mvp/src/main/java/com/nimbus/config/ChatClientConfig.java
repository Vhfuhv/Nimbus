package com.nimbus.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring AI ChatClient 配置
 */
@Configuration
public class ChatClientConfig {

    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        你是 Nimbus 智能天气助手。
                        你会基于给定的“城市、今日天气、穿衣建议”和用户问题，输出简洁、可执行的建议。
                        要求：
                        1) 用中文回答；
                        2) 不要编造未提供的数据；
                        3) 给出明确可执行的穿搭与是否带伞建议。
                        """)
                .build();
    }
}
