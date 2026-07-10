package cn.kuzuanpa.organeffects.api.extension;

import cn.kuzuanpa.organeffects.api.EffectDefinition;
import cn.kuzuanpa.organeffects.common.capability.IEffectHolder;
import net.minecraft.world.entity.player.Player;

public interface PointExecutor {
    String type();

    void execute(PointExecutionContext context, EffectDefinition.BonusAction action);

    record PointExecutionContext(Player player, IEffectHolder holder, PointUsageResolver usageResolver) {
        public PointUsage resolveUsage(EffectDefinition.BonusAction action) {
            return usageResolver.resolve(action);
        }
    }

    @FunctionalInterface
    interface PointUsageResolver {
        PointUsage resolve(EffectDefinition.BonusAction action);
    }

    record PointUsage(long usedPoints) {
    }
}
