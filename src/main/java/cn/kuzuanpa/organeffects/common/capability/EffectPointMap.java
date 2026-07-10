package cn.kuzuanpa.organeffects.common.capability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class EffectPointMap {
    private final Map<String, Long> points = new LinkedHashMap<>();

    public void add(String key, long amount) {
        points.merge(key, amount, Long::sum);
        if (points.getOrDefault(key, 0L) == 0L) {
            points.remove(key);
        }
    }

    public void set(String key, long value) {
        if (value == 0L) {
            points.remove(key);
            return;
        }
        points.put(key, value);
    }

    public long get(String key) {
        return points.getOrDefault(key, 0L);
    }

    public void clear() {
        points.clear();
    }

    public Map<String, Long> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(points));
    }
}
