package com.nimbus.agentai.agent;

import com.nimbus.agentai.model.City;
import com.nimbus.agentai.model.ClothingAdvice;
import com.nimbus.agentai.model.DailyWeather;
import com.nimbus.agentai.model.ForecastDayBrief;
import lombok.Data;

import java.util.List;

@Data
public class AgentRunContext {
    private City lastCity;
    private DailyWeather lastWeather;
    private List<ForecastDayBrief> lastForecast;
    private ClothingAdvice lastAdvice;
}
