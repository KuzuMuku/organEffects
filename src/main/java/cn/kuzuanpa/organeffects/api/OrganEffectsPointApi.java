/*
 * This class was created by <kuzuanpa>. It is distributed as
 * part of the organEffects Mod. Get the Source Code in github:
 * https://github.com/KuzuMuku/organEffects
 *
 * organEffects is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.

 * organEffects is Open Source and distributed under the
 * AGPLv3 License: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 */

package cn.kuzuanpa.organeffects.api;

import cn.kuzuanpa.organeffects.common.capability.EffectCapabilities;
import cn.kuzuanpa.organeffects.common.capability.IEffectHolder;
import cn.kuzuanpa.organeffects.common.effect.EffectRecalculationService;
import cn.kuzuanpa.organeffects.common.data.PointConfigData;
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
    public static final String MARK_PREFIX = PointConfigData.MARK_PREFIX;
    public static final String TARGET_MARK_SOURCE = PointConfigData.TARGET_MARK_SOURCE;

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

    public static Map<String, Long> getClientSyncedPoints(LivingEntity entity) {
        return cn.kuzuanpa.organeffects.common.effect.OrganStatService.getClientSyncedPoints(entity);
    }

    public static long getClientSyncedPoint(LivingEntity entity, String pointKey) {
        return cn.kuzuanpa.organeffects.common.effect.OrganStatService.getClientSyncedPoint(entity, pointKey);
    }

    public static boolean isMarkPoint(String pointKey) {
        return PointConfigData.isMarkPoint(pointKey);
    }

    public static Map<String, Long> getMarks(LivingEntity entity) {
        IEffectHolder holder = getHolder(entity);
        if (holder == null) {
            return Map.of();
        }
        Map<String, Long> marks = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : holder.getEffectPoints().entrySet()) {
            if (isMarkPoint(entry.getKey()) && entry.getValue() != null && entry.getValue() > 0L) {
                marks.put(entry.getKey(), entry.getValue());
            }
        }
        return marks;
    }

    public static long addMarkPoint(LivingEntity target, String pointId, long amount) {
        return addSourcePoint(target, TARGET_MARK_SOURCE, markPointKey(pointId), amount);
    }

    public static long consumeMarkPoint(LivingEntity target, String pointId, long amount) {
        return consumeSourcePoint(target, TARGET_MARK_SOURCE, markPointKey(pointId), amount);
    }

    public static long clearMarkPoint(LivingEntity target, String pointId) {
        return clearSourcePoint(target, TARGET_MARK_SOURCE, markPointKey(pointId));
    }

    public static void clearMarks(LivingEntity target) {
        IEffectHolder holder = getHolder(target);
        if (holder == null || !isServerSide(target)) {
            return;
        }
        for (Map.Entry<String, Map<String, Long>> sourceEntry : holder.getPointSources().entrySet()) {
            String sourceTag = sourceEntry.getKey();
            for (String pointKey : Map.copyOf(sourceEntry.getValue()).keySet()) {
                if (!isMarkPoint(pointKey)) {
                    continue;
                }
                if ("runtime".equals(sourceTag)) {
                    holder.clearRuntimePoint(pointKey);
                } else {
                    holder.clearSourcePoint(sourceTag, pointKey);
                }
            }
        }
    }

    public static String markPointKey(String pointId) {
        String value = pointId == null ? "" : pointId;
        if (value.isBlank() || MARK_PREFIX.equals(value)) {
            return "";
        }
        return value.startsWith(MARK_PREFIX) ? value : MARK_PREFIX + value;
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
