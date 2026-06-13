package cn.kuzuanpa.organeffectprocessor.api;

import java.util.List;

public record EffectDefinition(
        String trigger,
        long value,
        List<Grant> grants
) {
    public EffectDefinition {
        grants = List.copyOf(grants);
    }

    public record Grant(String type, String id, long amount) {
    }
}
