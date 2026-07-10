package cn.kuzuanpa.organeffects.api.extension;

import cn.kuzuanpa.organeffects.common.effect.EffectRecalculationService;
import java.util.Map;
import net.minecraft.world.entity.Entity;

@FunctionalInterface
public interface RecomputeCallback {
    void afterRecompute(RecomputeContext context);

    record RecomputeContext(
            Entity entity,
            EffectRecalculationService.EvaluationContext evaluationContext,
            Map<String, Long> oldPoints,
            Map<String, Long> newPoints
    ) {
    }
}
