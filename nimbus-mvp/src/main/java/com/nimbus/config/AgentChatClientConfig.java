package com.nimbus.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 模式专用的 ChatClient 配置。
 * 这里的系统提示词约束模型输出为 JSON，便于服务端解析执行工具。
 */
@Configuration
public class AgentChatClientConfig {

    @Bean(name = "agentChatClient")
    public ChatClient agentChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        You are Nimbus, an AI weather assistant running in agent mode.
                        You can call tools to get structured data. Use tools when needed.
                        Respond in Chinese. Be concise and actionable.
                        If the city is missing, ask a short follow-up question to confirm the city.
                        You must output a single JSON object only (no extra text).
                        Allowed outputs:
                        - Tool call:
                          {"type":"tool","name":"extract_city","input":{"text":"..."}}
                          {"type":"tool","name":"get_weather_today","input":{"cityName":"..."}}
                          {"type":"tool","name":"get_clothing_advice","input":{"dailyWeather":{...}}}
                        - Final answer:
                          {"type":"final","content":"..."}
                        """)
                .build();
    }
}
