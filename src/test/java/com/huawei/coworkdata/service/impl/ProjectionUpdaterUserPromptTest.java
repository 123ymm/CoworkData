package com.huawei.coworkdata.service.impl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 反射测 ProjectionUpdater 的 user_prompt 兑底（sessions NOT NULL）。
 */
class ProjectionUpdaterUserPromptTest {

    @Test
    void payloadStringFallsBackWhenNullOrMissing() throws Exception {
        Method m = ProjectionUpdaterServiceImpl.class.getDeclaredMethod(
                "payloadString", Map.class, String.class, String.class, String.class);
        m.setAccessible(true);

        Map<String, Object> empty = new HashMap<>();
        assertEquals("", m.invoke(null, empty, "user_prompt", "userPrompt", ""));

        Map<String, Object> explicitNull = new HashMap<>();
        explicitNull.put("user_prompt", null);
        assertEquals("", m.invoke(null, explicitNull, "user_prompt", "userPrompt", ""));

        Map<String, Object> camel = new HashMap<>();
        camel.put("userPrompt", "hi");
        assertEquals("hi", m.invoke(null, camel, "user_prompt", "userPrompt", ""));

        Map<String, Object> snake = new HashMap<>();
        snake.put("user_prompt", "hello");
        assertEquals("hello", m.invoke(null, snake, "user_prompt", "userPrompt", ""));
    }

    @Test
    void firstStringReturnsNullWhenAbsent() throws Exception {
        Method m = ProjectionUpdaterServiceImpl.class.getDeclaredMethod(
                "firstString", Map.class, String.class, String.class);
        m.setAccessible(true);
        assertNull(m.invoke(null, new HashMap<>(), "user_prompt", "userPrompt"));
    }
}
