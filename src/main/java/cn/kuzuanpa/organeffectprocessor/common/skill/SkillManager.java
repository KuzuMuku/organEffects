package cn.kuzuanpa.organeffectprocessor.common.skill;

import cn.kuzuanpa.organeffectprocessor.api.extension.SkillExecutor;
import cn.kuzuanpa.organeffectprocessor.common.capability.EffectCapabilities;
import cn.kuzuanpa.organeffectprocessor.common.capability.IEffectHolder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

public class SkillManager {
    private static final Map<String, SkillDefinition> SKILLS = new HashMap<>();
    private static final Map<String, SkillExecutor> SKILL_EXECUTORS = new HashMap<>();
    private static final Map<UUID, Map<String, Integer>> PLAYER_SKILL_LEVELS = new HashMap<>();
    private static final Map<UUID, String> CLIENT_SELECTED_SKILLS = new HashMap<>();

    public static void registerDefaults() {
        registerSkill(new SkillDefinition(
                "organeffectprocessor:wonder_sight",
                "point.organeffectprocessor.skill.organeffectprocessor.wonder_sight",
                "point.organeffectprocessor.skill.organeffectprocessor.wonder_sight.desc",
                List.of(),
                5
        ));
        registerSkillExecutor("organeffectprocessor:wonder_sight", (player, level) -> {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * (10 + level * 5), 0,
                    false, false, true));
            return true;
        });
        registerSkill(new SkillDefinition(
                "organeffectprocessor:water_breathing",
                "point.organeffectprocessor.skill.organeffectprocessor.water_breathing",
                "point.organeffectprocessor.skill.organeffectprocessor.water_breathing.desc",
                List.of(),
                5
        ));
        registerSkillExecutor("organeffectprocessor:water_breathing", (player, level) -> {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * (15 + level * 5), 0,
                    false, false, true));
            return true;
        });
        registerSkill(new SkillDefinition(
                "organeffectprocessor:double_jump",
                "point.organeffectprocessor.skill.organeffectprocessor.double_jump",
                "point.organeffectprocessor.skill.organeffectprocessor.double_jump.desc",
                List.of(),
                5
        ));
        registerSkillExecutor("organeffectprocessor:double_jump", (player, level) -> {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 20 * (10 + level * 5), Math.max(0, level - 1),
                    false, false, true));
            return true;
        });
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
                definition = new SkillDefinition(skillId, skillId, skillId, List.of(), Integer.MAX_VALUE);
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
            player.displayClientMessage(Component.translatable("message.organeffectprocessor.skill.unavailable").withStyle(ChatFormatting.RED), true);
            return false;
        }

        SkillDefinition skill = SKILLS.get(normalizedSkillId);
        if (skill == null) {
            player.displayClientMessage(Component.translatable("message.organeffectprocessor.skill.invalid").withStyle(ChatFormatting.RED), true);
            return false;
        }
        SkillExecutor executor = SKILL_EXECUTORS.get(normalizedSkillId);
        if (executor == null) {
            player.displayClientMessage(Component.translatable("message.organeffectprocessor.skill.invalid").withStyle(ChatFormatting.RED), true);
            return false;
        }

        setSelectedSkillId(player, normalizedSkillId);
        if (!executor.cast(player, level)) {
            return false;
        }
        player.displayClientMessage(Component.translatable("message.organeffectprocessor.skill.cast", Component.translatable(skill.nameKey()))
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

    public static void syncClientSkillState(UUID playerId, String selectedSkillId, Map<String, Integer> levels) {
        PLAYER_SKILL_LEVELS.put(playerId, new LinkedHashMap<>(levels));
        CLIENT_SELECTED_SKILLS.put(playerId, normalizeSkillId(selectedSkillId));
    }

    private static String normalizeSkillId(String skillId) {
        ResourceLocation parsed = ResourceLocation.tryParse(skillId);
        return parsed != null ? parsed.toString() : (skillId == null ? "" : skillId);
    }
}
