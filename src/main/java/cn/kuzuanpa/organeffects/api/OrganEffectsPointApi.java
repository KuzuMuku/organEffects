package cn.kuzuanpa.organeffects.api;

import cn.kuzuanpa.organeffects.common.capability.EffectCapabilities;
import cn.kuzuanpa.organeffects.common.capability.IEffectHolder;
import cn.kuzuanpa.organeffects.common.effect.EffectRecalculationService;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Public point API for compat mods.
 *
 * This API works even when the player has no organs installed because the effect
 * holder capability is attached to every living entity.
 */
public final class OrganEffectsPointApi {
    private OrganEffectsPointApi() {
    }

    public static Map<String, Long> getPoints(LivingEntity entity) {
        IEffectHolder holder = getHolder(entity);
        return holder == null ? Map.of() : holder.getEffectPoints();
    }

    public static long getPoint(LivingEntity entity, String pointKey) {
        IEffectHolder holder = getHolder(entity);
        if (holder == null || pointKey == null || pointKey.isBlank()) {
            return 0L;
        }
        return holder.getEffectPoints().getOrDefault(pointKey, 0L);
    }

    public static long addSourcePoint(LivingEntity entity, String sourceTag, String pointKey, long amount) {
        IEffectHolder holder = getHolder(entity);
        if (holder == null || !isServerSide(entity) || amount == 0L || pointKey == null || pointKey.isBlank()) {
            return 0L;
        }
        long value = holder.addSourcePoint(normalizeSourceTag(sourceTag), pointKey, amount);
        refreshPlayer(entity);
        return value;
    }

    public static long setSourcePoint(LivingEntity entity, String sourceTag, String pointKey, long value) {
        IEffectHolder holder = getHolder(entity);
        if (holder == null || !isServerSide(entity) || pointKey == null || pointKey.isBlank()) {
            return 0L;
        }
        String normalizedSource = normalizeSourceTag(sourceTag);
        Map<String, Long> points = new LinkedHashMap<>(holder.getPointsForSource(normalizedSource));
        if (value == 0L) {
            points.remove(pointKey);
        } else {
            points.put(pointKey, value);
        }
        holder.replaceSourcePoints(normalizedSource, points);
        refreshPlayer(entity);
        return value;
    }

    public static void replaceSourcePoints(LivingEntity entity, String sourceTag, Map<String, Long> points) {
        IEffectHolder holder = getHolder(entity);
        if (holder == null || !isServerSide(entity)) {
            return;
        }
        holder.replaceSourcePoints(normalizeSourceTag(sourceTag), points == null ? Map.of() : points);
        refreshPlayer(entity);
    }

    public static long consumeSourcePoint(LivingEntity entity, String sourceTag, String pointKey, long amount) {
        IEffectHolder holder = getHolder(entity);
        if (holder == null || !isServerSide(entity) || amount <= 0L || pointKey == null || pointKey.isBlank()) {
            return 0L;
        }
        long used = holder.consumeSourcePoint(normalizeSourceTag(sourceTag), pointKey, amount);
        if (used > 0L) {
            refreshPlayer(entity);
        }
        return used;
    }

    public static long clearSourcePoint(LivingEntity entity, String sourceTag, String pointKey) {
        IEffectHolder holder = getHolder(entity);
        if (holder == null || !isServerSide(entity) || pointKey == null || pointKey.isBlank()) {
            return 0L;
        }
        long removed = holder.clearSourcePoint(normalizeSourceTag(sourceTag), pointKey);
        if (removed != 0L) {
            refreshPlayer(entity);
        }
        return removed;
    }

    public static int clearSourcesWithPrefix(LivingEntity entity, String prefix) {
        IEffectHolder holder = getHolder(entity);
        if (holder == null || !isServerSide(entity) || prefix == null || prefix.isBlank()) {
            return 0;
        }
        int removed = holder.clearSourcesWithPrefix(prefix);
        if (removed > 0) {
            refreshPlayer(entity);
        }
        return removed;
    }

    public static long addRuntimePoint(LivingEntity entity, String pointKey, long amount, long durationTicks) {
        IEffectHolder holder = getHolder(entity);
        if (holder == null || !isServerSide(entity) || amount == 0L || pointKey == null || pointKey.isBlank()) {
            return 0L;
        }
        long expireAtTick = durationTicks > 0L && entity.level() != null ? entity.level().getGameTime() + durationTicks : 0L;
        long value = holder.addRuntimePoint(pointKey, amount, expireAtTick);
        refreshPlayer(entity);
        return value;
    }

    public static long setRuntimePoint(LivingEntity entity, String pointKey, long value, long durationTicks) {
        IEffectHolder holder = getHolder(entity);
        if (holder == null || !isServerSide(entity) || pointKey == null || pointKey.isBlank()) {
            return 0L;
        }
        holder.clearRuntimePoint(pointKey);
        if (value != 0L) {
            long expireAtTick = durationTicks > 0L && entity.level() != null ? entity.level().getGameTime() + durationTicks : 0L;
            holder.addRuntimePoint(pointKey, value, expireAtTick);
        }
        refreshPlayer(entity);
        return value;
    }

    public static long consumeRuntimePoint(LivingEntity entity, String pointKey, long amount) {
        IEffectHolder holder = getHolder(entity);
        if (holder == null || !isServerSide(entity) || amount <= 0L || pointKey == null || pointKey.isBlank()) {
            return 0L;
        }
        long used = holder.consumeRuntimePoint(pointKey, amount);
        if (used > 0L) {
            refreshPlayer(entity);
        }
        return used;
    }

    public static long clearRuntimePoint(LivingEntity entity, String pointKey) {
        IEffectHolder holder = getHolder(entity);
        if (holder == null || !isServerSide(entity) || pointKey == null || pointKey.isBlank()) {
            return 0L;
        }
        long removed = holder.clearRuntimePoint(pointKey);
        if (removed != 0L) {
            refreshPlayer(entity);
        }
        return removed;
    }

    public static void refresh(LivingEntity entity) {
        if (entity instanceof Player player && !player.level().isClientSide()) {
            EffectRecalculationService.reapply(player);
        }
    }

    public static void recompute(LivingEntity entity) {
        if (entity != null && isServerSide(entity)) {
            EffectRecalculationService.recompute(entity);
        }
    }

    private static IEffectHolder getHolder(LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        return entity.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
    }

    private static String normalizeSourceTag(String sourceTag) {
        return sourceTag == null || sourceTag.isBlank() ? "compat" : sourceTag;
    }

    private static boolean isServerSide(LivingEntity entity) {
        return entity != null && !entity.level().isClientSide();
    }

    private static void refreshPlayer(LivingEntity entity) {
        if (isServerSide(entity) && entity instanceof Player player) {
            EffectRecalculationService.reapply(player);
        }
    }
}
