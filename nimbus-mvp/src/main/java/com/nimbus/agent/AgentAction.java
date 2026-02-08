package com.nimbus.agent;

import java.util.Map;

/**
 * 模型输出的动作结构（工具调用或最终回答）。
 */
public class AgentAction {
    private String type;
    private String name;
    private Map<String, Object> input;
    private String content;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
