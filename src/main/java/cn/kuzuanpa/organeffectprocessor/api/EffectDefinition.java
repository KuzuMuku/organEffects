package cn.kuzuanpa.organeffectprocessor.api;

import java.util.List;

public record EffectDefinition(
        List<Condition> conditions,
        List<Grant> grants,
        List<EventRule> events
) {
    public EffectDefinition {
        conditions = List.copyOf(conditions);
        grants = List.copyOf(grants);
        events = List.copyOf(events);
    }

    public record Condition(
            String type,
            String operator,
            Long value,
            Long min,
            Long max,
            String edge,
            String weather,
            String time,
            String scope,
            String bodyPart,
            Integer slot,
            String organ
    ) {
    }

    public record Grant(String type, String id, long amount) {
    }

    public record EventRule(
            String type,
            Long distance,
            String source,
            String item,
            String itemTag,
            String block,
            String blockTag,
            boolean foodOnly,
            List<PointMutation> addPoints,
            List<PointMutation> consumePoints,
            List<BonusAction> actions
    ) {
        public EventRule {
            addPoints = List.copyOf(addPoints);
            consumePoints = List.copyOf(consumePoints);
            actions = List.copyOf(actions);
        }
    }

    public record PointMutation(
            String type,
            String id,
            long amount,
            String source,
            ChanceConfig chance
    ) {
    }

    public record ChanceConfig(
            Double base,
            Double luckyStep,
            Double max
    ) {
    }

    public record BonusAction(
            String type,
            double amountPerPoint,
            String pointType,
            String pointId,
            String source,
            long maxConsume
    ) {
    }
}
