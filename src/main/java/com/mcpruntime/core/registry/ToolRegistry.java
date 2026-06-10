package com.mcpruntime.core.registry;

import com.mcpruntime.core.registry.exception.DuplicateToolException;
import com.mcpruntime.core.registry.exception.ToolNotFoundException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ToolRegistry {

    private final ConcurrentHashMap<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final List<ToolRegistryListener> listeners = new CopyOnWriteArrayList<>();

    public void register(ToolDefinition tool) {
        ToolDefinition old = tools.putIfAbsent(tool.getName(), tool);
        if (old != null) {
            throw new DuplicateToolException(
                "Tool '" + tool.getName() + "' already registered");
        }
        listeners.forEach(l -> l.onToolRegistered(tool));
    }

    public void registerAll(Collection<ToolDefinition> tools) {
        tools.forEach(this::register);
    }

    public Optional<ToolDefinition> lookup(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public ToolDefinition getRequired(String name) {
        return lookup(name).orElseThrow(
            () -> new ToolNotFoundException("Tool not found: " + name));
    }

    public List<ToolDefinition> list() {
        return List.copyOf(tools.values());
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public int size() {
        return tools.size();
    }

    public void addListener(ToolRegistryListener listener) {
        this.listeners.add(listener);
    }

    public interface ToolRegistryListener {
        default void onToolRegistered(ToolDefinition tool) {}

        default void onToolRemoved(String toolName) {}
    }
}
