package cn.kuzuanpa.organeffects.api.extension;

import cn.kuzuanpa.organeffects.common.effect.EffectRecalculationService;
import net.minecraft.world.entity.Entity;

public interface PointProducer {
    String id();

    void producePoints(PointProductionContext context, MutablePointSink sink);

    record PointProductionContext(Entity entity, EffectRecalculationService.EvaluationContext evaluationContext) {
    }

    @FunctionalInterface
    interface MutablePointSink {
        void add(String pointType, String pointId, long amount);
    }
}
