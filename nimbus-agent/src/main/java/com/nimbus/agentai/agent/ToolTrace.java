package com.nimbus.agentai.agent;

import lombok.Data;

@Data
public class ToolTrace {
    private String name;
    private long startTs;
    private long durationMs;
    private String inputSummary;
    private String outputSummary;
    private String status;
    private String error;
}

