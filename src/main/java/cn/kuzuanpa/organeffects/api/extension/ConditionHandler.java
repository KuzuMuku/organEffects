package cn.kuzuanpa.organeffects.api.extension;

import cn.kuzuanpa.organapi.api.query.OrganPosition;
import cn.kuzuanpa.organeffects.api.EffectDefinition;
import cn.kuzuanpa.organeffects.common.effect.EffectRecalculationService;

@FunctionalInterface
public interface ConditionHandler {
    boolean test(ConditionContext context, EffectDefinition.Condition condition);

    record ConditionContext(
            EffectRecalculationService.EvaluationContext evaluationContext,
            OrganPosition position
    ) {
    }
}
