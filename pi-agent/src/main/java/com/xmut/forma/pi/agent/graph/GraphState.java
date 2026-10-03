package com.xmut.forma.pi.agent.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图状态容器。
 * 功能描述：不可变持有键值；未注册 Channel 时按 last-value 合并。
 */
public final class GraphState {

    private final Map<String, Object> values;

    private GraphState(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new HashMap<>(values));
    }

    public static GraphState empty() {
        return new GraphState(new HashMap<>());
    }

    public static GraphState create(Map<String, Object> initialValues) {
        GraphState state = empty();
        return initialValues == null || initialValues.isEmpty() ? state : state.withUpdate(initialValues);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object val = values.get(key);
        if (val == null) {
            return null;
        }
        return type.cast(val);
    }

    public Object get(String key) {
        return values.get(key);
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public GraphState withUpdate(Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            return this;
        }
        Map<String, Object> newValues = new HashMap<>(this.values);
        for (Map.Entry<String, Object> e : updates.entrySet()) {
            newValues.put(e.getKey(), copyValue(e.getValue()));
        }
        return new GraphState(newValues);
    }

    public boolean containsKey(String key) {
        return values.containsKey(key);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object copyValue(Object value) {
        if (value instanceof List) {
            return Collections.unmodifiableList(new ArrayList<>((List) value));
        }
        if (value instanceof Map) {
            return Collections.unmodifiableMap(new HashMap<>((Map) value));
        }
        if (value instanceof Collection) {
            return Collections.unmodifiableList(new ArrayList<>((Collection) value));
        }
        return value;
    }
}
