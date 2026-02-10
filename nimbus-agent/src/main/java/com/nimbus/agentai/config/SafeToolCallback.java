package com.nimbus.agentai.config;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.util.StringUtils;

/**
 * Some LLM providers occasionally emit tool calls with empty argument strings (""), which would fail JSON parsing.
 * This wrapper normalizes blank arguments to "{}" so object-typed tool inputs can be deserialized.
 */
public class SafeToolCallback implements ToolCallback {

    private final ToolCallback delegate;

    public SafeToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String arguments) {
        return delegate.call(normalize(arguments));
    }

    @Override
    public String call(String arguments, ToolContext toolContext) {
        return delegate.call(normalize(arguments), toolContext);
    }

    private static String normalize(String arguments) {
        if (!StringUtils.hasText(arguments)) {
            return "{}";
        }
        return arguments;
    }
}
