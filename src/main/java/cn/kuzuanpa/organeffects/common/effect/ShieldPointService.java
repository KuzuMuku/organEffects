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

package cn.kuzuanpa.organeffects.common.effect;

import cn.kuzuanpa.organeffects.common.capability.EffectCapabilities;
import cn.kuzuanpa.organeffects.common.capability.IEffectHolder;
import cn.kuzuanpa.organeffects.common.data.PointConfigData;
import cn.kuzuanpa.organeffects.common.network.OrganEffectsNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public final class ShieldPointService {
    private static final String SHIELD_PREFIX = "shield:";
    private static final String RUNTIME_SOURCE = "runtime";
    private static final String RUNTIME_PREFIX = "runtime:";
    private static final long RUNTIME_PULSE_DURATION_TICKS = 2L;

    private ShieldPointService() {
    }

    public static void handleLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || event.getAmount() <= 0.0F) {
            return;
        }
        IEffectHolder holder = entity.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder == null) {
            return;
        }

        List<ShieldEntry> shields = collectEligibleShields(holder, event.getSource());
        if (shields.isEmpty()) {
            return;
        }

        ShieldEntry selected = selectShield(shields, entity.getRandom());
        if (selected == null) {
            return;
        }

        float incomingDamage = event.getAmount();
        float absorbedDamage;
        float remainingDamage;
        boolean shieldBroken;

        if (selected.isBlockMode()) {
            selected.consumeAll(holder);
            absorbedDamage = incomingDamage;
            remainingDamage = 0.0F;
            shieldBroken = true;
        } else {
            absorbedDamage = Math.min(incomingDamage, selected.value());
            long used = selected.consume(holder, (long) Math.ceil(absorbedDamage));
            if (used <= 0L) {
                return;
            }
            absorbedDamage = Math.min(absorbedDamage, used);
            remainingDamage = Math.max(0.0F, incomingDamage - absorbedDamage);
            shieldBroken = selected.currentValue(holder) <= 0L;
        }

        if (absorbedDamage <= 0.0F) {
            return;
        }

        event.setAmount(remainingDamage);
        emitConfiguredRuntime(holder, entity, selected.config().onHitRuntime());
        if (shieldBroken) {
            emitConfiguredRuntime(holder, entity, selected.config().onBreakRuntime());
        }
        syncClient(entity, holder);
    }

    private static List<ShieldEntry> collectEligibleShields(IEffectHolder holder, DamageSource source) {
        String damageType = source == null ? "" : source.getMsgId();
        List<ShieldEntry> shields = new ArrayList<>();
        for (Map.Entry<String, Map<String, Long>> sourceEntry : holder.getPointSources().entrySet()) {
            String sourceTag = sourceEntry.getKey();
            for (Map.Entry<String, Long> pointEntry : sourceEntry.getValue().entrySet()) {
                String pointKey = pointEntry.getKey();
                long value = pointEntry.getValue();
                if (!pointKey.startsWith(SHIELD_PREFIX) || value <= 0L) {
                    continue;
                }
                PointConfigData.PointConfig config = PointConfigData.INSTANCE.getPointConfig(pointKey)
                        .orElseGet(ShieldPointService::defaultConfig);
                if (!matchesDamageType(config, damageType)) {
                    continue;
                }
                shields.add(new ShieldEntry(sourceTag, pointKey, value, config));
            }
        }
        return shields;
    }

    private static ShieldEntry selectShield(List<ShieldEntry> shields, RandomSource random) {
        if (shields.isEmpty()) {
            return null;
        }
        int highestPriority = shields.stream()
                .mapToInt(entry -> entry.config().priority())
                .max()
                .orElse(0);
        List<ShieldEntry> candidates = shields.stream()
                .filter(entry -> entry.config().priority() == highestPriority)
                .toList();
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static boolean matchesDamageType(PointConfigData.PointConfig config, String damageType) {
        if (config.damageTypes().isEmpty()) {
            return true;
        }
        boolean contains = config.damageTypes().contains(damageType);
        return config.damageTypeWhitelist() ? contains : !contains;
    }

    private static void emitConfiguredRuntime(IEffectHolder holder, LivingEntity entity, String configuredKey) {
        String pointKey = normalizeRuntimeKey(configuredKey);
        if (pointKey == null) {
            return;
        }
        holder.addRuntimePoint(pointKey, 1L, entity.level().getGameTime() + RUNTIME_PULSE_DURATION_TICKS);
    }

    private static String normalizeRuntimeKey(String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            return null;
        }
        return configuredKey.contains(":") ? configuredKey : RUNTIME_PREFIX + configuredKey;
    }

    private static void syncClient(LivingEntity entity, IEffectHolder holder) {
        if (entity instanceof ServerPlayer player) {
            OrganEffectsNetwork.syncClientPoints(player, PointConfigData.INSTANCE.collectClientSyncPoints(holder.getEffectPoints()));
        }
    }

    private static PointConfigData.PointConfig defaultConfig() {
        return new PointConfigData.PointConfig(
                null,
                null,
                0,
                List.of(),
                false,
                "spill",
                "",
                "",
                PointConfigData.DEFAULT_MARK_ICON,
                PointConfigData.DEFAULT_MARK_RENDER_SCALE,
                PointConfigData.DEFAULT_MARK_RENDER_OFFSET,
                PointConfigData.DEFAULT_MARK_TINT
        );
    }

    private record ShieldEntry(
            String sourceTag,
            String pointKey,
            long value,
            PointConfigData.PointConfig config
    ) {
        boolean isBlockMode() {
            return "block".equalsIgnoreCase(config.overflowMode());
        }

        long consume(IEffectHolder holder, long amount) {
            if (RUNTIME_SOURCE.equals(sourceTag)) {
                return holder.consumeRuntimePoint(pointKey, amount);
            }
            return holder.consumeSourcePoint(sourceTag, pointKey, amount);
        }

        void consumeAll(IEffectHolder holder) {
            if (RUNTIME_SOURCE.equals(sourceTag)) {
                holder.clearRuntimePoint(pointKey);
            } else {
                holder.clearSourcePoint(sourceTag, pointKey);
            }
        }

        long currentValue(IEffectHolder holder) {
            if (RUNTIME_SOURCE.equals(sourceTag)) {
                return holder.getRuntimePoints().getOrDefault(pointKey, 0L);
            }
            return holder.getPointsForSource(sourceTag).getOrDefault(pointKey, 0L);
        }
    }
}
