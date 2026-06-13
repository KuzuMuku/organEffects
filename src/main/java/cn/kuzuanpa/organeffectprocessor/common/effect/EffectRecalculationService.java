package cn.kuzuanpa.organeffectprocessor.common.effect;

import cn.kuzuanpa.organapi.api.organ.OrganDefinition;
import cn.kuzuanpa.organapi.api.query.OrganPosition;
import cn.kuzuanpa.organapi.api.query.OrganQueryService;
import cn.kuzuanpa.organapi.common.data.OrganRegistryAccess;
import cn.kuzuanpa.organeffectprocessor.api.EffectDefinition;
import cn.kuzuanpa.organeffectprocessor.common.capability.EffectCapabilities;
import cn.kuzuanpa.organeffectprocessor.common.capability.EffectPointMap;
import cn.kuzuanpa.organeffectprocessor.common.capability.IEffectHolder;
import cn.kuzuanpa.organeffectprocessor.common.data.OrganEffectData;
import cn.kuzuanpa.organeffectprocessor.common.network.OepNetwork;
import cn.kuzuanpa.organeffectprocessor.common.skill.SkillManager;
import cn.kuzuanpa.organeffectprocessor.common.sync.AttributeSyncer;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class EffectRecalculationService {
    private EffectRecalculationService() {
    }

    public static Map<String, Long> recompute(Entity entity) {
        IEffectHolder holder = entity.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder == null) {
            return Map.of();
        }

        EffectPointMap pointMap = new EffectPointMap();
        computeEffects(entity, pointMap);

        Map<String, Long> oldPoints = holder.getEffectPoints();
        Map<String, Long> newPoints = pointMap.snapshot();
        holder.setEffectPoints(newPoints);

        if (entity instanceof Player player) {
            AttributeSyncer.applyFromMap(player, oldPoints, newPoints);
            SkillManager.updatePlayerSkills(player, newPoints);
            if (player instanceof ServerPlayer serverPlayer) {
                OepNetwork.syncSkills(serverPlayer);
            }
        }
        return newPoints;
    }

    public static void reapply(Player player) {
        IEffectHolder holder = player.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder == null) {
            return;
        }
        Map<String, Long> points = holder.getEffectPoints();
        AttributeSyncer.applyFromMap(player, Map.of(), points);
        SkillManager.updatePlayerSkills(player, points);
        if (player instanceof ServerPlayer serverPlayer) {
            OepNetwork.syncSkills(serverPlayer);
        }
    }

    private static void computeEffects(Entity entity, EffectPointMap target) {
        target.clear();
        for (OrganPosition pos : OrganQueryService.getInstalledOrganPositions(entity)) {
            ResourceLocation organId = OrganRegistryAccess.getOrgan(pos.organ())
                    .map(OrganDefinition::id)
                    .orElse(null);
            if (organId == null) {
                continue;
            }

            for (EffectDefinition effect : OrganEffectData.INSTANCE.getEffectsForOrgan(organId)) {
                if (evaluateCondition(pos, effect)) {
                    for (EffectDefinition.Grant grant : effect.grants()) {
                        target.add(grant.type() + ":" + grant.id(), grant.amount());
                    }
                }
            }
        }
    }

    private static boolean evaluateCondition(OrganPosition pos, EffectDefinition effect) {
        return switch (effect.trigger()) {
            case "static" -> true;
            case "exactPos" -> pos.slotIndex() == effect.value();
            case "minPos" -> pos.slotIndex() >= effect.value();
            case "maxPos" -> pos.slotIndex() <= effect.value();
            default -> false;
        };
    }
}
