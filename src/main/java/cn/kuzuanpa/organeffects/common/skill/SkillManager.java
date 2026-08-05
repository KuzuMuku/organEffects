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

package cn.kuzuanpa.organeffects.common.skill;

import cn.kuzuanpa.organeffects.api.extension.SkillExecutor;
import cn.kuzuanpa.organeffects.common.capability.EffectCapabilities;
import cn.kuzuanpa.organeffects.common.capability.IEffectHolder;
import cn.kuzuanpa.organeffects.common.network.OrganEffectsNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SkillManager {
    private static final Map<String, SkillDefinition> SKILLS = new HashMap<>();
    private static final Map<String, SkillExecutor> SKILL_EXECUTORS = new HashMap<>();
    private static final Map<UUID, Map<String, Integer>> PLAYER_SKILL_LEVELS = new HashMap<>();
    private static final Map<UUID, String> CLIENT_SELECTED_SKILLS = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> CLIENT_SKILL_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, GuardianBeamState> ACTIVE_GUARDIAN_BEAMS = new HashMap<>();

    public static void registerDefaults() {
        registerSkill(new SkillDefinition(
                "organeffects:wonder_sight",
                "point.organeffects.skill.organeffects.wonder_sight",
                "point.organeffects.skill.organeffects.wonder_sight.desc",
                List.of(),
                20 * 15,
                5
        ));
        registerSkillExecutor("organeffects:wonder_sight", (player, level) -> {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * (10 + level * 5), 0,
                    false, false, true));
            return true;
        });
        registerSkill(new SkillDefinition(
                "organeffects:water_breathing",
                "point.organeffects.skill.organeffects.water_breathing",
                "point.organeffects.skill.organeffects.water_breathing.desc",
                List.of(),
                20 * 20,
                5
        ));
        registerSkillExecutor("organeffects:water_breathing", (player, level) -> {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * (15 + level * 5), 0,
                    false, false, true));
            return true;
        });
        registerSkill(new SkillDefinition(
                "organeffects:double_jump",
                "point.organeffects.skill.organeffects.double_jump",
                "point.organeffects.skill.organeffects.double_jump.desc",
                List.of(),
                20 * 5,
                5
        ));
        registerSkillExecutor("organeffects:double_jump", (player, level) -> {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 20 * (10 + level * 5), Math.max(0, level - 1),
                    false, false, true));
            return true;
        });
        registerSkill(new SkillDefinition(
                "organeffects:dragon_breath",
                "point.organeffects.skill.organeffects.dragon_breath",
                "point.organeffects.skill.organeffects.dragon_breath.desc",
                List.of(),
                20 * 12,
                5
        ));
        registerSkillExecutor("organeffects:dragon_breath", (player, level) -> {
            Vec3 look = player.getLookAngle().normalize();
            Vec3 spawn = getProjectileSpawnPosition(player, 1.5D);
            DragonFireball fireball = new DragonFireball(player.level(), player, look.x, look.y, look.z);
            fireball.moveTo(spawn.x, spawn.y, spawn.z, player.getYRot(), player.getXRot());
            fireball.setDeltaMovement(look.scale(0.55D + Math.min(level, 5) * 0.05D));
            player.level().addFreshEntity(fireball);
            return true;
        });
        registerSkill(new SkillDefinition(
                "organeffects:shulker_bolt",
                "point.organeffects.skill.organeffects.shulker_bolt",
                "point.organeffects.skill.organeffects.shulker_bolt.desc",
                List.of(),
                20 * 8,
                5
        ));
        registerSkillExecutor("organeffects:shulker_bolt", (player, level) -> {
            LivingEntity target = findLookTarget(player, 20.0D, 0.75D);
            if (target == null) {
                player.displayClientMessage(Component.translatable("message.organeffects.skill.no_target").withStyle(ChatFormatting.RED), true);
                return false;
            }
            Vec3 spawn = getProjectileSpawnPosition(player, 1.0D);
            ShulkerBullet bullet = new ShulkerBullet(player.level(), player, target, getDominantAxis(player.getLookAngle()));
            bullet.moveTo(spawn.x, spawn.y, spawn.z, player.getYRot(), player.getXRot());
            bullet.setDeltaMovement(player.getLookAngle().normalize().scale(0.35D + Math.min(level, 5) * 0.03D));
            player.level().addFreshEntity(bullet);
            return true;
        });
        registerSkill(new SkillDefinition(
                "organeffects:guardian_beam",
                "point.organeffects.skill.organeffects.guardian_beam",
                "point.organeffects.skill.organeffects.guardian_beam.desc",
                List.of(),
                20 * 10,
                5
        ));
        registerSkillExecutor("organeffects:guardian_beam", (player, level) -> {
            LivingEntity target = findLookTarget(player, 24.0D, 0.85D);
            if (target == null) {
                player.displayClientMessage(Component.translatable("message.organeffects.skill.no_target").withStyle(ChatFormatting.RED), true);
                return false;
            }
            long now = player.level().getGameTime();
            ACTIVE_GUARDIAN_BEAMS.put(player.getUUID(), new GuardianBeamState(target.getUUID(), level, now + 30L));
            OrganEffectsNetwork.sendBeamEffect(player, OrganEffectsNetwork.BeamEffectKind.GUARDIAN, target, 30);
            player.level().playSound(null, player.blockPosition(), SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 1.0F, 1.0F);
            return true;
        });
    }

    private static LivingEntity findLookTarget(Player player, double range, double dotThreshold) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        AABB area = player.getBoundingBox().inflate(range);
        return player.level().getEntitiesOfClass(LivingEntity.class, area, entity -> entity != player && entity.isAlive() && player.hasLineOfSight(entity)).stream()
                .filter(entity -> {
                    Vec3 toTarget = entity.getBoundingBox().getCenter().subtract(eye);
                    double distance = toTarget.length();
                    if (distance <= 0.001D || distance > range) {
                        return false;
                    }
                    return look.dot(toTarget.normalize()) >= dotThreshold;
                })
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
    }

    private static Vec3 getProjectileSpawnPosition(Player player, double forwardDistance) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        return eye.add(look.scale(forwardDistance)).add(0.0D, -0.2D, 0.0D);
    }

    private static Direction.Axis getDominantAxis(Vec3 direction) {
        double x = Math.abs(direction.x);
        double y = Math.abs(direction.y);
        double z = Math.abs(direction.z);
        if (y >= x && y >= z) {
            return Direction.Axis.Y;
        }
        return x >= z ? Direction.Axis.X : Direction.Axis.Z;
    }

    public static void tickActiveSkills(Player player) {
        tickGuardianBeam(player);
    }

    public static void clearTransientState(Player player) {
        ACTIVE_GUARDIAN_BEAMS.remove(player.getUUID());
    }

    private static void tickGuardianBeam(Player player) {
        GuardianBeamState state = ACTIVE_GUARDIAN_BEAMS.get(player.getUUID());
        if (state == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity rawTarget = serverLevel.getEntity(state.targetUuid());
        if (!(rawTarget instanceof LivingEntity target)
                || !target.isAlive()
                || player.distanceToSqr(target) > 24.0D * 24.0D
                || !player.hasLineOfSight(target)) {
            ACTIVE_GUARDIAN_BEAMS.remove(player.getUUID());
            OrganEffectsNetwork.clearBeamEffect(player, OrganEffectsNetwork.BeamEffectKind.GUARDIAN);
            return;
        }

        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 5, 0, false, true, true));

        if (serverLevel.getGameTime() < state.hitTick()) {
            return;
        }

        target.hurt(player.damageSources().indirectMagic(player, player), 5.0F + state.level() * 2.0F);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * (2 + state.level()), 1, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * (3 + state.level()), 0, false, true, true));
        serverLevel.playSound(null, target.blockPosition(), SoundEvents.GUARDIAN_HURT, SoundSource.PLAYERS, 1.0F, 0.9F);
        ACTIVE_GUARDIAN_BEAMS.remove(player.getUUID());
        OrganEffectsNetwork.clearBeamEffect(player, OrganEffectsNetwork.BeamEffectKind.GUARDIAN);
    }

    private record GuardianBeamState(UUID targetUuid, int level, long hitTick) {
    }

    public static void registerSkill(SkillDefinition skill) {
        SKILLS.put(normalizeSkillId(skill.id()), skill);
    }

    public static void registerSkillExecutor(String skillId, SkillExecutor executor) {
        SKILL_EXECUTORS.put(normalizeSkillId(skillId), executor);
    }

    public static void updatePlayerSkills(Player player, Map<String, Long> points) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : points.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("skill:")) {
                continue;
            }

            String skillId = normalizeSkillId(key.substring("skill:".length()));
            int level = entry.getValue().intValue();
            if (level > 0) {
                levels.put(skillId, level);
            }
        }
        PLAYER_SKILL_LEVELS.put(player.getUUID(), levels);

        IEffectHolder holder = player.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder == null) {
            return;
        }
        String selected = normalizeSkillId(holder.getSelectedSkillId());
        if (selected.isBlank() || !levels.containsKey(selected)) {
            selected = levels.keySet().stream().findFirst().orElse("");
            holder.setSelectedSkillId(selected);
        }
        CLIENT_SELECTED_SKILLS.put(player.getUUID(), selected);
    }

    public static int getPlayerSkillLevel(Player player, String skillId) {
        Map<String, Integer> levels = PLAYER_SKILL_LEVELS.get(player.getUUID());
        return levels != null ? levels.getOrDefault(normalizeSkillId(skillId), 0) : 0;
    }

    public static List<SkillDefinition> getAvailableSkills(Player player) {
        Map<String, Integer> levels = PLAYER_SKILL_LEVELS.getOrDefault(player.getUUID(), Map.of());
        Map<String, SkillDefinition> available = new LinkedHashMap<>();
        for (String skillId : levels.keySet()) {
            SkillDefinition definition = SKILLS.get(skillId);
            if (definition == null) {
                definition = new SkillDefinition(skillId, skillId, skillId, List.of(), 0, Integer.MAX_VALUE);
            }
            available.put(skillId, definition);
        }
        return new ArrayList<>(available.values());
    }

    public static Map<String, Integer> getSkillLevels(Player player) {
        return new LinkedHashMap<>(PLAYER_SKILL_LEVELS.getOrDefault(player.getUUID(), Map.of()));
    }

    public static boolean castSelectedSkill(Player player) {
        return castSkill(player, getSelectedSkillId(player));
    }

    public static boolean castSkill(Player player, String skillId) {
        String normalizedSkillId = normalizeSkillId(skillId);
        int level = getPlayerSkillLevel(player, normalizedSkillId);
        if (level <= 0) {
            player.displayClientMessage(Component.translatable("message.organeffects.skill.unavailable").withStyle(ChatFormatting.RED), true);
            return false;
        }

        SkillDefinition skill = SKILLS.get(normalizedSkillId);
        if (skill == null) {
            player.displayClientMessage(Component.translatable("message.organeffects.skill.invalid").withStyle(ChatFormatting.RED), true);
            return false;
        }
        SkillExecutor executor = SKILL_EXECUTORS.get(normalizedSkillId);
        if (executor == null) {
            player.displayClientMessage(Component.translatable("message.organeffects.skill.invalid").withStyle(ChatFormatting.RED), true);
            return false;
        }

        long remainingCooldown = getRemainingCooldownTicks(player, normalizedSkillId);
        if (remainingCooldown > 0L) {
            player.displayClientMessage(Component.translatable("message.organeffects.skill.cooldown", formatCooldownSeconds(remainingCooldown))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        setSelectedSkillId(player, normalizedSkillId);
        if (!executor.cast(player, level)) {
            return false;
        }
        applyCooldown(player, skill, normalizedSkillId);
        if (player instanceof ServerPlayer serverPlayer) {
            OrganEffectsNetwork.syncSkills(serverPlayer);
        }
        player.displayClientMessage(Component.translatable("message.organeffects.skill.cast", Component.translatable(skill.nameKey()))
                .withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    public static String getSelectedSkillId(Player player) {
        if (player.level().isClientSide) {
            return CLIENT_SELECTED_SKILLS.getOrDefault(player.getUUID(), "");
        }
        IEffectHolder holder = player.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        return holder == null ? "" : normalizeSkillId(holder.getSelectedSkillId());
    }

    public static void setSelectedSkillId(Player player, String skillId) {
        String normalizedSkillId = normalizeSkillId(skillId);
        CLIENT_SELECTED_SKILLS.put(player.getUUID(), normalizedSkillId);
        IEffectHolder holder = player.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder != null) {
            holder.setSelectedSkillId(normalizedSkillId);
        }
    }

    public static boolean selectSkill(Player player, String skillId) {
        String normalizedSkillId = normalizeSkillId(skillId);
        if (normalizedSkillId.isBlank() || getPlayerSkillLevel(player, normalizedSkillId) <= 0) {
            return false;
        }
        setSelectedSkillId(player, normalizedSkillId);
        if (player instanceof ServerPlayer serverPlayer) {
            OrganEffectsNetwork.syncSkills(serverPlayer);
        }
        return true;
    }

    public static long getRemainingCooldownTicks(Player player, String skillId) {
        String normalizedSkillId = normalizeSkillId(skillId);
        if (normalizedSkillId.isBlank()) {
            return 0L;
        }
        long expireAt = 0L;
        if (player.level().isClientSide) {
            expireAt = CLIENT_SKILL_COOLDOWNS.getOrDefault(player.getUUID(), Map.of()).getOrDefault(normalizedSkillId, 0L);
        } else {
            IEffectHolder holder = player.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
            if (holder != null) {
                expireAt = holder.getSkillCooldownExpiration(normalizedSkillId);
            }
        }
        return Math.max(0L, expireAt - player.level().getGameTime());
    }

    public static Map<String, Long> getCooldownRemainingTicks(Player player) {
        Map<String, Long> remaining = new LinkedHashMap<>();
        for (SkillDefinition skill : getAvailableSkills(player)) {
            long ticks = getRemainingCooldownTicks(player, skill.id());
            if (ticks > 0L) {
                remaining.put(skill.id(), ticks);
            }
        }
        return remaining;
    }

    public static void syncClientSkillState(UUID playerId, String selectedSkillId, Map<String, Integer> levels, Map<String, Long> cooldownExpirations) {
        PLAYER_SKILL_LEVELS.put(playerId, new LinkedHashMap<>(levels));
        CLIENT_SELECTED_SKILLS.put(playerId, normalizeSkillId(selectedSkillId));
        CLIENT_SKILL_COOLDOWNS.put(playerId, new LinkedHashMap<>(cooldownExpirations));
    }

    private static String normalizeSkillId(String skillId) {
        ResourceLocation parsed = ResourceLocation.tryParse(skillId);
        return parsed != null ? parsed.toString() : skillId;
    }

    private static void applyCooldown(Player player, SkillDefinition skill, String normalizedSkillId) {
        if (skill.cooldownTicks() <= 0) {
            return;
        }
        IEffectHolder holder = player.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder == null) {
            return;
        }
        holder.setSkillCooldownExpiration(normalizedSkillId, player.level().getGameTime() + skill.cooldownTicks());
    }

    private static String formatCooldownSeconds(long ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0D);
    }
}
