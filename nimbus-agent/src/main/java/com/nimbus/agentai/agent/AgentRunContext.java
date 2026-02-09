package com.nimbus.agentai.agent;

import com.nimbus.agentai.model.City;
import com.nimbus.agentai.model.ClothingAdvice;
import com.nimbus.agentai.model.DailyWeather;
import lombok.Data;

@Data
public class AgentRunContext {
    private City lastCity;
    private DailyWeather lastWeather;
    private ClothingAdvice lastAdvice;
}

