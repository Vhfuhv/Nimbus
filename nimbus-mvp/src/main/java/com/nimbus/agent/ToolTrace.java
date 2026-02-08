package com.nimbus.agent;

/**
 * 工具调用追踪信息（用于可观测性）。
 */
public class ToolTrace {
    private String name;
    private long startTs;
    private long durationMs;
    private String inputSummary;
    private String outputSummary;
    private String status;
    private String error;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getStartTs() {
        return startTs;
    }

    public void setStartTs(long startTs) {
        this.startTs = startTs;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public void setInputSummary(String inputSummary) {
        this.inputSummary = inputSummary;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public void setOutputSummary(String outputSummary) {
        this.outputSummary = outputSummary;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
