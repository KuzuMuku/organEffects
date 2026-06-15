package cn.kuzuanpa.organeffectprocessor.api.extension;

import cn.kuzuanpa.organapi.api.query.OrganPosition;
import cn.kuzuanpa.organeffectprocessor.api.EffectDefinition;
import cn.kuzuanpa.organeffectprocessor.common.effect.EffectRecalculationService;

@FunctionalInterface
public interface ConditionHandler {
    boolean test(ConditionContext context, EffectDefinition.Condition condition);

    record ConditionContext(
            EffectRecalculationService.EvaluationContext evaluationContext,
            OrganPosition position
    ) {
    }
}
