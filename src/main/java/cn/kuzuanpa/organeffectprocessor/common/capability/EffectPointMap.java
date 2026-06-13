package cn.kuzuanpa.organeffectprocessor.common.capability;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class EffectPointMap {
    private final Map<String, Long> points = new HashMap<>();

    public void add(String key, long amount) {
        points.merge(key, amount, Long::sum);
    }

    public void set(String key, long value) {
        points.put(key, value);
    }

    public long get(String key) {
        return points.getOrDefault(key, 0L);
    }

    public void clear() {
        points.clear();
    }

    public Map<String, Long> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(points));
    }
}
