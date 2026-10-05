package com.xmut.forma.common.output;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class TurnAttachment {
    private final Map<String, Object> entries;

    private TurnAttachment(Map<String, Object> entries) {
        this.entries = entries;
    }

    public static TurnAttachment empty() {
        return new TurnAttachment(Collections.<String, Object>emptyMap());
    }

    public static TurnAttachment of(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return empty();
        }
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        if (copy.isEmpty()) {
            return empty();
        }
        return new TurnAttachment(Collections.unmodifiableMap(copy));
    }

    public Object get(String key) {
        return entries.get(key);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Map<String, Object> asMap() {
        return entries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TurnAttachment)) {
            return false;
        }
        TurnAttachment that = (TurnAttachment) o;
        return Objects.equals(entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(entries);
    }
}
