package cn.kuzuanpa.organeffectprocessor.common.effect;

import cn.kuzuanpa.organapi.api.query.OrganPosition;
import cn.kuzuanpa.organapi.api.query.OrganQueryService;
import cn.kuzuanpa.organapi.common.data.OrganRegistryAccess;
import cn.kuzuanpa.organeffectprocessor.api.EffectDefinition;
import cn.kuzuanpa.organeffectprocessor.api.extension.OepExtensionApi;
import cn.kuzuanpa.organeffectprocessor.api.extension.PointExecutor;
import cn.kuzuanpa.organeffectprocessor.common.capability.EffectCapabilities;
import cn.kuzuanpa.organeffectprocessor.common.capability.IEffectHolder;
import cn.kuzuanpa.organeffectprocessor.common.data.OrganEffectData;
import cn.kuzuanpa.organeffectprocessor.common.debug.OepDebug;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class RuntimePointExecutor {
    private RuntimePointExecutor() {
    }

    public static void execute(Entity entity) {
        if (!(entity instanceof Player player)) {
            return;
        }
        IEffectHolder holder = player.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder == null) {
            return;
        }
        holder.clearExpiredRuntimePoints(player.level().getGameTime());
        for (EffectInstance instance : collectEffectInstances(player)) {
            if (!EffectRecalculationService.evaluateConditions(player, instance.position(), instance.effect().conditions())) {
                continue;
            }
            for (EffectDefinition.BonusAction execution : instance.effect().executions()) {
                applyExecution(player, holder, instance, execution);
            }
        }
    }

    private static void applyExecution(Player player, IEffectHolder holder, EffectInstance instance, EffectDefinition.BonusAction execution) {
        PointExecutor executor = OepExtensionApi.getPointExecutor(execution.type());
        if (executor == null) {
            OepDebug.trace(player, "execution missing type=%s", execution.type());
            return;
        }
        String pointKey = execution.pointType() != null && execution.pointId() != null
                ? execution.pointType() + ":" + execution.pointId()
                : null;
        long runtimeAvailable = pointKey != null ? holder.getRuntimePoints().getOrDefault(pointKey, 0L) : 0L;
        long sourceAvailable = pointKey != null ? holder.getPooledSourcePoints(pointKey, execution.source()) : 0L;
        if (runtimeAvailable <= 0L && sourceAvailable <= 0L) {
            return;
        }
        executor.execute(new PointExecutor.PointExecutionContext(player, holder, action -> previewPointUsage(holder, action)), execution);
    }

    private static PointExecutor.PointUsage previewPointUsage(IEffectHolder holder, EffectDefinition.BonusAction execution) {
        if (execution.pointType() == null || execution.pointId() == null) {
            return new PointExecutor.PointUsage(0L);
        }
        long maxConsume = Math.max(0L, execution.maxConsume());
        if (maxConsume <= 0L) {
            return new PointExecutor.PointUsage(0L);
        }
        String pointKey = execution.pointType() + ":" + execution.pointId();
        long runtimeAvailable = holder.getRuntimePoints().getOrDefault(pointKey, 0L);
        if (runtimeAvailable > 0L) {
            return new PointExecutor.PointUsage(Math.min(runtimeAvailable, maxConsume));
        }
        long available = holder.getPooledSourcePoints(pointKey, execution.source());
        return new PointExecutor.PointUsage(Math.min(available, maxConsume));
    }

    public static PointExecutor.PointUsage consumePointUsage(Player player, IEffectHolder holder, EffectDefinition.BonusAction execution) {
        if (execution.pointType() == null || execution.pointId() == null) {
            OepDebug.trace(player, "resolve usage skipped missing point binding");
            return new PointExecutor.PointUsage(0L);
        }
        long maxConsume = Math.max(0L, execution.maxConsume());
        if (maxConsume <= 0L) {
            OepDebug.trace(player, "resolve usage skipped maxConsume=%d", execution.maxConsume());
            return new PointExecutor.PointUsage(0L);
        }
        String pointKey = execution.pointType() + ":" + execution.pointId();
        long runtimeAvailable = holder.getRuntimePoints().getOrDefault(pointKey, 0L);
        if (runtimeAvailable > 0L) {
            long capped = Math.min(runtimeAvailable, maxConsume);
            long used = execution.consumePoints() ? holder.consumeRuntimePoint(pointKey, capped) : capped;
            long remaining = holder.getRuntimePoints().getOrDefault(pointKey, 0L);
            if (execution.consumePoints()) {
                OepDebug.trace(player, "resolve runtime %s available=%d used=%d remaining=%d consume=%s", pointKey, runtimeAvailable, used, remaining, execution.consumePoints());
            }
            return new PointExecutor.PointUsage(Math.max(0L, used));
        }
        long available = holder.getPooledSourcePoints(pointKey, execution.source());
        long capped = Math.min(available, maxConsume);
        long used = execution.consumePoints() ? holder.consumePooledSourcePoints(pointKey, execution.source(), capped) : capped;
        long remaining = holder.getPooledSourcePoints(pointKey, execution.source());
        if (execution.consumePoints()) {
            OepDebug.trace(player, "resolve pooled %s source=%s available=%d used=%d remaining=%d consume=%s",
                    pointKey, execution.source(), available, used, remaining, execution.consumePoints());
        }
        return new PointExecutor.PointUsage(Math.max(0L, used));
    }

    private static List<EffectInstance> collectEffectInstances(Player player) {
        Map<String, EffectInstance> effects = new LinkedHashMap<>();
        for (OrganPosition position : OrganQueryService.getInstalledOrganPositions(player)) {
            ResourceLocation organId = OrganRegistryAccess.getOrgan(position.organ()).map(definition -> definition.id()).orElse(null);
            if (organId == null) {
                continue;
            }
            int effectIndex = 0;
            for (EffectDefinition effect : OrganEffectData.INSTANCE.getEffectsForOrgan(organId)) {
                if (!effect.executions().isEmpty()) {
                    effects.put(effectKey(organId, position, effectIndex), new EffectInstance(organId, position, effect, effectIndex));
                }
                effectIndex++;
            }
        }
        return List.copyOf(effects.values());
    }

    private static String resolveSource(EffectInstance instance, String declaredSource) {
        if (declaredSource == null || declaredSource.isBlank() || "self".equals(declaredSource)) {
            return EffectRecalculationService.ORGAN_INSTANCE_SOURCE_PREFIX
                    + instance.organId() + "@" + instance.position().bodyPartId() + "#" + instance.position().slotIndex() + "/event/" + instance.effectIndex();
        }
        return declaredSource;
    }

    private static String effectKey(ResourceLocation organId, OrganPosition position, int effectIndex) {
        return organId + "@" + position.bodyPartId() + "#" + position.slotIndex() + "/effect/" + effectIndex;
    }

    private record EffectInstance(ResourceLocation organId, OrganPosition position, EffectDefinition effect, int effectIndex) {
    }
}
