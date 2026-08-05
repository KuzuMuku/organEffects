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

package cn.kuzuanpa.organeffects.common.point;

import cn.kuzuanpa.organapi.api.organ.OrganDefinition;
import cn.kuzuanpa.organapi.api.query.OrganPosition;
import cn.kuzuanpa.organapi.common.data.OrganRegistryAccess;
import cn.kuzuanpa.organeffects.api.EffectDefinition;
import cn.kuzuanpa.organeffects.api.extension.OrganEffectsExtensionApi;
import cn.kuzuanpa.organeffects.common.data.OrganEffectData;
import cn.kuzuanpa.organeffects.common.effect.EffectRecalculationService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

public final class OrganEffectDisplayBuilder {
    private static final int DEFAULT_TOOLTIP_LINES = 4;
    private static final ChatFormatting POINT_COLOR = ChatFormatting.BLUE;
    private static final ChatFormatting CONDITION_COLOR = ChatFormatting.YELLOW;
    private static final ChatFormatting GRANT_COLOR = ChatFormatting.GREEN;
    private static final ChatFormatting EVENT_COLOR = ChatFormatting.LIGHT_PURPLE;
    private static final ChatFormatting EXECUTION_COLOR = ChatFormatting.RED;
    private static final ChatFormatting LABEL_COLOR = ChatFormatting.DARK_GRAY;
    private static final ChatFormatting SEPARATOR_COLOR = ChatFormatting.GRAY;

    private OrganEffectDisplayBuilder() {
    }

    public static List<Component> buildViewerEffectLines(Player player, List<OrganPosition> positions) {
        List<ViewerEffectEntry> entries = new ArrayList<>();
        for (OrganPosition position : positions) {
            OrganDefinition definition = OrganRegistryAccess.getOrgan(position.organ()).orElse(null);
            if (definition == null) {
                continue;
            }
            int effectIndex = 0;
            for (EffectDefinition effect : OrganEffectData.INSTANCE.getEffectsForOrgan(definition.id())) {
                boolean conditionsMet = EffectRecalculationService.evaluateConditions(player, position, effect.conditions());
                appendViewerGrantEntries(entries, definition, position, effectIndex, effect.conditions(), effect.grants(), conditionsMet);
                appendViewerDerivedGrantEntries(entries, definition, position, effectIndex, effect.conditions(),
                        OrganEffectData.INSTANCE.getDerivedGrantRulesForEffect(definition.id(), effectIndex), conditionsMet);
                appendViewerEventEntries(entries, definition, position, effectIndex, effect.conditions(), effect.events(), conditionsMet);
                appendViewerExecutionEntries(entries, definition, position, effectIndex, effect.conditions(), effect.executions(), conditionsMet);
                effectIndex++;
            }
        }
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<String, MergedViewerEntry> merged = new LinkedHashMap<>();
        for (ViewerEffectEntry entry : entries) {
            merged.computeIfAbsent(entry.kind() + "\n" + entry.text().getString(), key -> new MergedViewerEntry(entry.kind(), entry.text()))
                    .add(entry.hover(), entry.amountText());
        }
        List<Component> lines = new ArrayList<>();
        for (MergedViewerEntry entry : merged.values()) {
            lines.add(withHover(entry.renderText(), entry.buildHover()));
        }
        return lines;
    }

    public static List<Component> buildTooltipLines(OrganDefinition definition, List<EffectDefinition> effects, boolean expanded) {
        List<Component> allLines = new ArrayList<>();
        for (String tooltip : definition.tooltips()) {
            allLines.add(Component.translatableWithFallback(tooltip, tooltip).withStyle(ChatFormatting.GRAY));
        }

        List<DisplayLine> effectDisplayLines = new ArrayList<>();
        int effectIndex = 0;
        for (EffectDefinition effect : effects) {
            appendGrantLines(effectDisplayLines, effect.conditions(), effect.grants(), false);
            appendDerivedGrantLines(effectDisplayLines, effect.conditions(),
                    OrganEffectData.INSTANCE.getDerivedGrantRulesForEffect(definition.id(), effectIndex), false);
            appendEventLines(effectDisplayLines, effect.conditions(), effect.events());
            appendExecutionLines(effectDisplayLines, effect.conditions(), effect.executions());
            effectIndex++;
        }
        if (effectDisplayLines.isEmpty()) {
            return allLines;
        }

        List<Component> effectLines = new ArrayList<>();
        for (DisplayLine line : effectDisplayLines) {
            effectLines.add(withHover(line.text().copy(), line.hover()));
        }

        allLines.add(Component.translatable("message.organeffects.effects.tooltip.header").withStyle(ChatFormatting.AQUA));
        if (expanded) {
            allLines.addAll(effectLines);
            allLines.add(Component.translatable("message.organeffects.effects.tooltip.expanded").withStyle(ChatFormatting.DARK_GRAY));
            return allLines;
        }

        int visibleCount = Math.min(DEFAULT_TOOLTIP_LINES, effectLines.size());
        for (int index = 0; index < visibleCount; index++) {
            allLines.add(effectLines.get(index));
        }
        if (effectLines.size() > visibleCount) {
            allLines.add(Component.translatable("message.organeffects.effects.tooltip.more", effectLines.size() - visibleCount)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        allLines.add(Component.translatable("message.organeffects.effects.tooltip.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        return allLines;
    }

    public static Component getOrganHeader(OrganDefinition definition) {
        MutableComponent name = Component.translatable("item." + definition.itemId().getNamespace() + "." + definition.itemId().getPath());
        if (name.getString().equals("item." + definition.itemId().getNamespace() + "." + definition.itemId().getPath())) {
            Item item = ForgeRegistries.ITEMS.getValue(definition.itemId());
            if (item != null) {
                name = item.getDescription().copy();
            }
        }
        return Component.literal("- ").append(name.withStyle(ChatFormatting.AQUA));
    }

    private static void appendGrantLines(List<DisplayLine> target, List<EffectDefinition.Condition> conditions,
                                         List<EffectDefinition.Grant> grants, boolean includeConditionlessHeader) {
        if (grants.isEmpty()) {
            return;
        }
        MutableComponent prefix = describeConditionPrefix(conditions, includeConditionlessHeader);
        for (EffectDefinition.Grant grant : grants) {
            if (grant.hidden()) {
                continue;
            }
            Component point = formatPointAmount(grant.type(), grant.id(), grant.amount());
            String translationKey = "skill".equals(grant.type())
                    ? "message.organeffects.effects.provides_active_skill"
                    : "message.organeffects.effects.provides";
            target.add(new DisplayLine("grant",
                    withKindLabel("grant", prefix.copy().append(Component.translatable(translationKey, point).withStyle(GRANT_COLOR))),
                    buildConditionHover(conditions)));
        }
    }

    private static void appendDerivedGrantLines(List<DisplayLine> target, List<EffectDefinition.Condition> conditions,
                                                List<OrganEffectData.DerivedGrantRule> rules, boolean includeConditionlessHeader) {
        if (rules.isEmpty()) {
            return;
        }
        MutableComponent prefix = describeConditionPrefix(conditions, includeConditionlessHeader);
        for (OrganEffectData.DerivedGrantRule rule : rules) {
            target.add(new DisplayLine("grant",
                    withKindLabel("grant", prefix.copy().append(describeDerivedGrant(rule))),
                    buildConditionHover(conditions)));
        }
    }

    private static void appendEventLines(List<DisplayLine> target, List<EffectDefinition.Condition> conditions, List<EffectDefinition.EventRule> events) {
        for (EffectDefinition.EventRule event : events) {
            if (event.hidden()) {
                continue;
            }
            Component customDisplay = resolveCustomDisplay(event.customDisplayKey());
            if (customDisplay != null) {
                target.add(new DisplayLine("event", withKindLabel("event", customDisplay.copy().withStyle(EVENT_COLOR)), buildEventHover(conditions, event)));
                continue;
            }
            List<Component> pieces = new ArrayList<>();
            for (EffectDefinition.PointMutation mutation : event.addPoints()) {
                pieces.add(Component.translatable("message.organeffects.effects.gains",
                        formatPointAmount(mutation.type(), mutation.id(), mutation.amount())).withStyle(GRANT_COLOR));
            }
            for (EffectDefinition.PointMutation mutation : event.consumePoints()) {
                pieces.add(Component.translatable("message.organeffects.effects.consumes",
                        formatPointAmount(mutation.type(), mutation.id(), mutation.amount())).withStyle(EXECUTION_COLOR));
            }
            for (EffectDefinition.BonusAction action : event.actions()) {
                pieces.add(describeAction(action).copy().withStyle(EVENT_COLOR));
            }
            if (pieces.isEmpty()) {
                continue;
            }
            MutableComponent prefix = describeConditionPrefix(conditions, false)
                    .append(Component.translatable("message.organeffects.effects.on_event", describeEvent(event)).withStyle(EVENT_COLOR))
                    .append(Component.literal(": ").withStyle(SEPARATOR_COLOR));
            Component body = joinComponents(pieces);
            target.add(new DisplayLine("event", withKindLabel("event", prefix.append(body)), buildEventHover(conditions, event)));
        }
    }

    private static void appendExecutionLines(List<DisplayLine> target, List<EffectDefinition.Condition> conditions, List<EffectDefinition.BonusAction> executions) {
        for (EffectDefinition.BonusAction execution : executions) {
            if (execution.hidden()) {
                continue;
            }
            Component customDisplay = resolveCustomDisplay(execution.customDisplayKey());
            if (customDisplay != null) {
                target.add(new DisplayLine("execution", withKindLabel("execution", customDisplay.copy()), buildExecutionHover(conditions, execution)));
                continue;
            }
            MutableComponent prefix = describeConditionPrefix(conditions, false)
                    .append(Component.translatable("message.organeffects.effects.when_points", describePointBinding(execution)))
                    .append(Component.literal(": ").withStyle(SEPARATOR_COLOR));
            target.add(new DisplayLine("execution", withKindLabel("execution", prefix.append(describeAction(execution).copy().withStyle(EXECUTION_COLOR))), buildExecutionHover(conditions, execution)));
        }
    }

    private static void appendViewerGrantEntries(List<ViewerEffectEntry> target, OrganDefinition definition, OrganPosition position, int effectIndex,
                                                 List<EffectDefinition.Condition> conditions, List<EffectDefinition.Grant> grants, boolean conditionsMet) {
        if (conditions.isEmpty() || conditions.stream().allMatch(condition -> condition == null || Objects.equals(condition.type(), "static"))) {
            return;
        }
        for (EffectDefinition.Grant grant : grants) {
            if (grant.hidden()) {
                continue;
            }
            MutableComponent text = describeConditionPrefix(conditions, false, conditionsMet)
                    .append(Component.translatable("skill".equals(grant.type())
                                    ? "message.organeffects.effects.provides_active_skill"
                                    : "message.organeffects.effects.provides",
                            EffectPointTextHelper.getDisplayName(grant.type() + ":" + grant.id())).withStyle(GRANT_COLOR));
            String amountText = "+" + grant.amount();
            target.add(new ViewerEffectEntry("grant", withKindLabel("grant", text), amountText, buildViewerHover(definition, position, effectIndex, conditions, conditionsMet, null, null)));
        }
    }

    private static void appendViewerDerivedGrantEntries(List<ViewerEffectEntry> target, OrganDefinition definition, OrganPosition position, int effectIndex,
                                                        List<EffectDefinition.Condition> conditions, List<OrganEffectData.DerivedGrantRule> rules, boolean conditionsMet) {
        if (rules.isEmpty()) {
            return;
        }
        for (OrganEffectData.DerivedGrantRule rule : rules) {
            MutableComponent text = describeConditionPrefix(conditions, false, conditionsMet).append(describeDerivedGrant(rule));
            target.add(new ViewerEffectEntry("grant", withKindLabel("grant", text), null, buildViewerHover(definition, position, effectIndex, conditions, conditionsMet, null, null)));
        }
    }

    private static void appendViewerEventEntries(List<ViewerEffectEntry> target, OrganDefinition definition, OrganPosition position, int effectIndex,
                                                 List<EffectDefinition.Condition> conditions, List<EffectDefinition.EventRule> events, boolean conditionsMet) {
        for (EffectDefinition.EventRule event : events) {
            if (event.hidden()) {
                continue;
            }
            Component customDisplay = resolveCustomDisplay(event.customDisplayKey());
            if (customDisplay != null) {
                target.add(new ViewerEffectEntry("event", withKindLabel("event", customDisplay.copy().withStyle(EVENT_COLOR)), null, buildViewerHover(definition, position, effectIndex, conditions, conditionsMet, event, null)));
                continue;
            }
            for (EffectDefinition.PointMutation mutation : event.addPoints()) {
                MutableComponent text = describeConditionPrefix(conditions, false, conditionsMet)
                        .append(Component.translatable("message.organeffects.effects.on_event", describeEvent(event))
                        .withStyle(EVENT_COLOR)
                        .append(Component.literal(": ").withStyle(SEPARATOR_COLOR)))
                        .append(Component.translatable("message.organeffects.effects.gains",
                                EffectPointTextHelper.getDisplayName(mutation.type() + ":" + mutation.id())).withStyle(GRANT_COLOR));
                String amountText = "+" + mutation.amount();
                target.add(new ViewerEffectEntry("event", withKindLabel("event", text), amountText, buildViewerHover(definition, position, effectIndex, conditions, conditionsMet, event, null)));
            }
        }
    }

    private static void appendViewerExecutionEntries(List<ViewerEffectEntry> target, OrganDefinition definition, OrganPosition position, int effectIndex,
                                                     List<EffectDefinition.Condition> conditions, List<EffectDefinition.BonusAction> executions, boolean conditionsMet) {
        for (EffectDefinition.BonusAction execution : executions) {
            if (execution.hidden()) {
                continue;
            }
            Component customDisplay = resolveCustomDisplay(execution.customDisplayKey());
            if (customDisplay != null) {
                target.add(new ViewerEffectEntry("execution", withKindLabel("execution", customDisplay.copy()), null, buildViewerHover(definition, position, effectIndex, conditions, conditionsMet, null, execution)));
                continue;
            }
            MutableComponent text = describeConditionPrefix(conditions, false, conditionsMet)
                    .append(Component.translatable("message.organeffects.effects.when_points", describePointBinding(execution))
                    .append(Component.literal(": ").withStyle(SEPARATOR_COLOR))
                    .append(describeAction(execution).copy().withStyle(EXECUTION_COLOR)));
            target.add(new ViewerEffectEntry("execution", withKindLabel("execution", text), null, buildViewerHover(definition, position, effectIndex, conditions, conditionsMet, null, execution)));
        }
    }

    private static MutableComponent describeConditionPrefix(List<EffectDefinition.Condition> conditions, boolean includeAlways) {
        List<Component> readableConditions = describeConditions(conditions);
        if (readableConditions.isEmpty()) {
            return includeAlways
                    ? Component.translatable("message.organeffects.effects.always_prefix").withStyle(CONDITION_COLOR)
                    : Component.empty();
        }
        return Component.translatable("message.organeffects.effects.when_prefix", joinComponents(readableConditions))
                .withStyle(CONDITION_COLOR)
                .append(Component.literal(": ").withStyle(SEPARATOR_COLOR));
    }

    private static MutableComponent describeConditionPrefix(List<EffectDefinition.Condition> conditions, boolean includeAlways, boolean conditionsMet) {
        ChatFormatting color = conditionsMet ? CONDITION_COLOR : ChatFormatting.GOLD;
        List<Component> readableConditions = describeConditions(conditions);
        if (readableConditions.isEmpty()) {
            return includeAlways
                    ? Component.translatable("message.organeffects.effects.always_prefix").withStyle(color)
                    : Component.empty();
        }
        return Component.translatable("message.organeffects.effects.when_prefix", joinComponents(readableConditions))
                .withStyle(color)
                .append(Component.literal(": ").withStyle(SEPARATOR_COLOR));
    }

    private static MutableComponent describeDerivedGrant(OrganEffectData.DerivedGrantRule rule) {
        return Component.translatable(
                "message.organeffects.effects.provides_per_points",
                formatPointAmount(rule.targetType(), rule.targetId(), rule.amount()),
                rule.per(),
                EffectPointTextHelper.getDisplayName(rule.fromType() + ":" + rule.fromId())
        ).withStyle(GRANT_COLOR);
    }

    private static List<Component> describeConditions(List<EffectDefinition.Condition> conditions) {
        List<Component> readable = new ArrayList<>();
        for (EffectDefinition.Condition condition : conditions) {
            if (condition == null || Objects.equals(condition.type(), "static")) {
                continue;
            }
            readable.add(describeCondition(condition));
        }
        return readable;
    }

    private static Component describeCondition(EffectDefinition.Condition condition) {
        Component customDisplay = resolveCustomDisplay(condition.configString("custom_display_key"));
        if (customDisplay != null) {
            return customDisplay;
        }
        Component custom = OrganEffectsExtensionApi.renderCondition(condition);
        if (custom != null) {
            return custom;
        }
        return switch (condition.type()) {
            case "health" -> describeThresholdCondition("message.organeffects.effects.condition.health", condition.configString("mode"), condition.configString("op"),
                    condition.configLong("value"), condition.configLong("min"), condition.configLong("max"));
            case "hunger" -> describeThresholdCondition("message.organeffects.effects.condition.hunger", condition.configString("mode"), condition.configString("op"),
                    condition.configLong("value"), condition.configLong("min"), condition.configLong("max"));
            case "air" -> "underwater".equals(condition.configString("mode"))
                    ? Component.translatable("message.organeffects.effects.condition.air_underwater")
                    : describeThresholdCondition("message.organeffects.effects.condition.air", condition.configString("mode"), condition.configString("op"),
                    condition.configLong("value"), null, null);
            case "xp" -> describeThresholdCondition("message.organeffects.effects.condition.xp", condition.configString("mode"), condition.configString("op"),
                    condition.configLong("value"), condition.configLong("min"), condition.configLong("max"));
            case "status_effect" -> Component.translatable("message.organeffects.effects.condition.status_effect",
                    describeMobEffect(condition.configString("effect")));
            case "attribute" -> describeThresholdCondition("message.organeffects.effects.condition.attribute",
                    describeAttributeId(condition.configString("attribute")).getString(), condition.configString("op"), condition.configLong("value"),
                    condition.configLong("min"), condition.configLong("max"));
            case "movement_state" -> Component.translatable("message.organeffects.effects.condition.movement_state",
                    translateKey("message.organeffects.effects.state.", condition.configString("state")));
            case "environment_state" -> Component.translatable("message.organeffects.effects.condition.environment_state",
                    translateKey("message.organeffects.effects.state.", condition.configString("state")));
            case "equipment" -> describeEquipmentCondition(condition);
            case "enchantment" -> Component.translatable("message.organeffects.effects.condition.enchantment",
                    translateKey("message.organeffects.effects.slot.", condition.configString("slot")),
                    describeItemId(condition.configString("enchantment")),
                    formatOperator(condition.configString("op")), valueOf(condition.configLong("value")));
            case "nearby_entity" -> describeThresholdCondition("message.organeffects.effects.condition.nearby_entity",
                    describeEntityId(condition.configString("entity"), condition.configString("entity_tag")).getString(),
                    condition.configString("op"), condition.configLong("value"), condition.configLong("min"), condition.configLong("max"));
            case "moon_phase" -> condition.configString("mode") != null
                    ? Component.translatable("message.organeffects.effects.condition.moon_phase_mode",
                    translateKey("message.organeffects.effects.moon_phase.", condition.configString("mode")))
                    : Component.translatable("message.organeffects.effects.condition.moon_phase_value",
                    formatOperator(condition.configString("op")), valueOf(condition.configLong("value")));
            case "biome_category" -> Component.translatable("message.organeffects.effects.condition.biome_category",
                    condition.configString("value"));
            case "dimension_type" -> Component.translatable("message.organeffects.effects.condition.dimension_type",
                    describeDimensionId(condition.configString("value")));
            case "slot_index" -> Component.translatable("message.organeffects.effects.condition.slot_index",
                    formatOperator(condition.configString("op")), valueOf(condition.configLong("value")));
            case "distance_to_edge" -> Component.translatable("message.organeffects.effects.condition.distance_to_edge",
                    translateKey("message.organeffects.effects.edge.", condition.configString("edge")),
                    formatOperator(condition.configString("op")), valueOf(condition.configLong("value")));
            case "weather" -> Component.translatable("message.organeffects.effects.condition.weather",
                    translateKey("message.organeffects.effects.weather.", firstConditionString(condition, "weather", "value")));
            case "time" -> describeTimeCondition(condition);
            case "has_organ" -> Component.translatable("message.organeffects.effects.condition.has_organ",
                    translateScope(condition.configString("scope")), describeOrganId(condition.configString("organ")));
            case "biome" -> describeBiomeCondition(condition);
            case "dimid" -> Component.translatable("message.organeffects.effects.condition.dimid", describeDimensionId(firstConditionString(condition, "value_id", "value")));
            case "lightlevel" -> Component.translatable("message.organeffects.effects.condition.lightlevel",
                    formatOperator(condition.configString("op")), valueOf(condition.configLong("value")));
            case "stepon" -> describeStepOnCondition(condition);
            default -> Component.literal(condition.type());
        };
    }

    private static Component describeBiomeCondition(EffectDefinition.Condition condition) {
        String biomeId = firstConditionString(condition, "value_id", "value");
        String biomeTag = firstConditionString(condition, "biome_tag", "tag");
        if (biomeId != null && biomeTag != null) {
            return Component.translatable("message.organeffects.effects.condition.biome_and_tag",
                    describeBiomeId(biomeId), biomeTag);
        }
        if (biomeTag != null) {
            return Component.translatable("message.organeffects.effects.condition.biome_tag", biomeTag);
        }
        return Component.translatable("message.organeffects.effects.condition.biome", describeBiomeId(biomeId));
    }

    private static Component describeStepOnCondition(EffectDefinition.Condition condition) {
        String blockId = condition.configString("block");
        String blockTag = condition.configString("block_tag");
        if (blockId != null && blockTag != null) {
            return Component.translatable("message.organeffects.effects.condition.stepon_and_tag",
                    describeBlockId(blockId), blockTag);
        }
        if (blockTag != null) {
            return Component.translatable("message.organeffects.effects.condition.stepon_tag", blockTag);
        }
        return Component.translatable("message.organeffects.effects.condition.stepon", describeBlockId(blockId));
    }

    private static Component describeTimeCondition(EffectDefinition.Condition condition) {
        String mode = condition.configString("mode");
        Long min = condition.configLong("min");
        Long max = condition.configLong("max");
        if (mode != null) {
            return Component.translatable("message.organeffects.effects.condition.time_mode",
                    translateKey("message.organeffects.effects.time.", mode));
        }
        if (min != null || max != null) {
            return Component.translatable("message.organeffects.effects.condition.time_range",
                    valueOf(min), valueOf(max));
        }
        return Component.translatable("message.organeffects.effects.condition.time_value",
                formatOperator(condition.configString("op")), valueOf(condition.configLong("value")));
    }

    private static String firstConditionString(EffectDefinition.Condition condition, String... keys) {
        for (String key : keys) {
            String value = condition.configString(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Component describeEvent(EffectDefinition.EventRule event) {
        Component custom = OrganEffectsExtensionApi.renderEvent(event);
        if (custom != null) {
            return custom;
        }
        MutableComponent eventName = translateKey("message.organeffects.effects.event.", event.type());
        List<Component> filters = new ArrayList<>();
        if (event.foodOnly()) {
            filters.add(Component.translatable("message.organeffects.effects.event.filter.food_only"));
        }
        if (event.item() != null) {
            filters.add(Component.translatable("message.organeffects.effects.event.filter.item", describeItemId(event.item())));
        }
        if (event.itemTag() != null) {
            filters.add(Component.translatable("message.organeffects.effects.event.filter.item_tag", event.itemTag()));
        }
        if (event.block() != null) {
            filters.add(Component.translatable("message.organeffects.effects.event.filter.block", describeBlockId(event.block())));
        }
        if (event.blockTag() != null) {
            filters.add(Component.translatable("message.organeffects.effects.event.filter.block_tag", event.blockTag()));
        }
        if (event.distance() != null) {
            filters.add(Component.translatable("message.organeffects.effects.event.filter.distance", event.distance()));
        }
        if ("tick".equals(event.type())) {
            Long intervalTicks = event.configLong("interval_ticks");
            if (intervalTicks == null) {
                intervalTicks = event.configLong("interval");
            }
            if (intervalTicks != null && intervalTicks > 1L) {
                filters.add(Component.translatable("message.organeffects.effects.event.filter.interval_ticks", intervalTicks));
            }
        }
        if (filters.isEmpty()) {
            return eventName;
        }
        return eventName.append(Component.literal(" (" + joinPlain(filters) + ")"));
    }

    private static Component describeAction(EffectDefinition.BonusAction action) {
        Component custom = OrganEffectsExtensionApi.renderAction(action);
        if (custom != null) {
            return custom;
        }
        return switch (action.type()) {
            case "damage_self" -> Component.translatable("message.organeffects.effects.action.damage_self", valueOf(action.amount()));
            case "damage_target" -> Component.translatable("message.organeffects.effects.action.damage_target", valueOf(action.amount()));
            case "knockback" -> Component.translatable("message.organeffects.effects.action.knockback", valueOf(action.amount()));
            case "launch" -> Component.translatable("message.organeffects.effects.action.launch", valueOf(action.configDouble("y")));
            case "teleport" -> describeTeleportAction(action);
            case "spawn_particle" -> Component.translatable("message.organeffects.effects.action.spawn_particle",
                    translateKey("message.organeffects.effects.unknown.", action.configString("particle")));
            case "play_sound" -> Component.translatable("message.organeffects.effects.action.play_sound",
                    translateKey("message.organeffects.effects.unknown.", action.configString("sound")));
            case "remove_effect" -> Component.translatable("message.organeffects.effects.action.remove_effect", describeMobEffect(action.effectId()));
            case "clear_negative_effects" -> Component.translatable("message.organeffects.effects.action.clear_negative_effects");
            case "give_xp" -> Component.translatable("message.organeffects.effects.action.give_xp", valueOf(action.amount()));
            case "consume_hunger" -> Component.translatable("message.organeffects.effects.action.consume_hunger", valueOf(action.amount()));
            case "restore_air" -> Component.translatable("message.organeffects.effects.action.restore_air", valueOf(action.amount()));
            case "set_fire" -> Component.translatable("message.organeffects.effects.action.set_fire", valueOf(action.amount()));
            case "extinguish" -> Component.translatable("message.organeffects.effects.action.extinguish");
            case "summon_entity" -> Component.translatable("message.organeffects.effects.action.summon_entity",
                    describeEntityId(action.configString("entity"), null));
            case "drop_items" -> Component.translatable("message.organeffects.effects.action.drop_items", valueOf(action.items().size()));
            case "set_cooldown" -> Component.translatable("message.organeffects.effects.action.set_cooldown", valueOf(action.durationTicks() != null ? action.durationTicks() : action.amount()));
            case "consume_item" -> Component.translatable("message.organeffects.effects.action.consume_item", valueOf(action.amount()));
            case "repair_item" -> Component.translatable("message.organeffects.effects.action.repair_item", valueOf(action.amount()));
            case "place_block" -> Component.translatable("message.organeffects.effects.action.place_block",
                    describeBlockId(action.configString("block")));
            case "convert_block" -> Component.translatable("message.organeffects.effects.action.convert_block",
                    describeBlockId(action.configString("from")), describeBlockId(action.configString("to")));
            case "force_target" -> Component.translatable("message.organeffects.effects.action.force_target", valueOf(action.amount()));
            case "apply_mob_effect" -> Component.translatable("message.organeffects.effects.action.apply_self_mob_effect",
                    describeMobEffect(action.effectId()), valueOf(action.durationTicks()), valueOf(action.amplifier()));
            case "apply_target_mob_effect" -> Component.translatable("message.organeffects.effects.action.apply_target_mob_effect",
                    describeMobEffect(action.effectId()), valueOf(action.durationTicks()), valueOf(action.amplifier()));
            case "heal" -> Component.translatable("message.organeffects.effects.action.heal", valueOf(action.amount()));
            case "grant_items" -> Component.translatable("message.organeffects.effects.action.grant_items",
                    valueOf(action.rolls()), valueOf(action.items().size()));
            case "taunt" -> Component.translatable("message.organeffects.effects.action.taunt",
                    translateKey("message.organeffects.effects.target.", action.target()),
                    valueOf(action.amount() != null ? action.amount() : 8.0D),
                    valueOf(action.durationTicks() != null && action.durationTicks() > 0 ? action.durationTicks() : 60));
            default -> Component.literal(action.type());
        };
    }

    private static Component describePointBinding(EffectDefinition.BonusAction action) {
        if (action.pointType() == null || action.pointId() == null) {
            return Component.translatable("message.organeffects.effects.unknown");
        }
        MutableComponent binding = EffectPointTextHelper.getDisplayName(action.pointType() + ":" + action.pointId());
        if (action.maxConsume() > 0L && action.maxConsume() != Long.MAX_VALUE) {
            binding = binding.append(Component.literal(" x" + action.maxConsume()).withStyle(ChatFormatting.GOLD));
        }
        return binding;
    }

    private static Component buildConditionHover(List<EffectDefinition.Condition> conditions) {
        List<Component> readableConditions = describeConditions(conditions);
        if (readableConditions.isEmpty()) {
            return Component.translatable("message.organeffects.effects.hover.always");
        }
        return Component.translatable("message.organeffects.effects.hover.conditions", joinComponents(readableConditions));
    }

    private static Component buildConditionHover(List<EffectDefinition.Condition> conditions, boolean conditionsMet) {
        List<Component> readableConditions = describeConditions(conditions);
        ChatFormatting color = conditionsMet ? CONDITION_COLOR : ChatFormatting.GOLD;
        Component status = Component.translatable(
                conditionsMet
                        ? "message.organeffects.effects.hover.status.met"
                        : "message.organeffects.effects.hover.status.unmet"
        ).withStyle(color);
        if (readableConditions.isEmpty()) {
            return status.copy()
                    .append(Component.literal(" "))
                    .append(Component.translatable("message.organeffects.effects.hover.always").copy().withStyle(color));
        }
        return status.copy()
                .append(Component.literal(" ").withStyle(SEPARATOR_COLOR))
                .append(Component.translatable("message.organeffects.effects.hover.conditions", joinComponents(readableConditions)).copy().withStyle(color));
    }

    private static Component buildEventHover(List<EffectDefinition.Condition> conditions, EffectDefinition.EventRule event) {
        MutableComponent hover = Component.empty().append(buildConditionHover(conditions));
        if (event.source() != null) {
            hover.append(Component.literal("\n")).append(Component.translatable("message.organeffects.effects.hover.source", event.source()));
        }
        return hover;
    }

    private static Component buildExecutionHover(List<EffectDefinition.Condition> conditions, EffectDefinition.BonusAction execution) {
        MutableComponent hover = Component.empty().append(buildConditionHover(conditions));
        if (execution.isPointsConsume()) {
            hover.append(Component.literal("\n")).append(Component.translatable("message.organeffects.effects.hover.consume_points"));
        }
        if (execution.source() != null) {
            hover.append(Component.literal("\n")).append(Component.translatable("message.organeffects.effects.hover.source", execution.source()));
        }
        return hover;
    }

    private static Component buildViewerHover(OrganDefinition definition, OrganPosition position, int effectIndex,
                                              List<EffectDefinition.Condition> conditions, boolean conditionsMet, EffectDefinition.EventRule event,
                                              EffectDefinition.BonusAction execution) {
        MutableComponent hover = Component.empty()
                .append(getOrganHeader(definition))
                .append(Component.literal("\n"))
                .append(Component.literal(position.bodyPartId() + " #" + position.slotIndex()).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("\n"))
                .append(Component.literal("effect #" + effectIndex).withStyle(ChatFormatting.DARK_GRAY));
        Component conditionHover = buildConditionHover(conditions, conditionsMet);
        if (!conditionHover.getString().isBlank()) {
            hover.append(Component.literal("\n")).append(conditionHover);
        }
        if (event != null && event.source() != null) {
            hover.append(Component.literal("\n")).append(Component.translatable("message.organeffects.effects.hover.source", event.source()));
        }
        if (execution != null) {
            if (execution.isPointsConsume()) {
                hover.append(Component.literal("\n")).append(Component.translatable("message.organeffects.effects.hover.consume_points"));
            }
            if (execution.source() != null) {
                hover.append(Component.literal("\n")).append(Component.translatable("message.organeffects.effects.hover.source", execution.source()));
            }
        }
        return hover;
    }

    private static Component formatPointAmount(String type, String id, Number amount) {
        String pointKey = type + ":" + id;
        return EffectPointTextHelper.getDisplayName(pointKey)
                .copy()
                .append(Component.literal(" +" + formatNumber(amount)).withStyle(ChatFormatting.GOLD))
                .withStyle(POINT_COLOR)
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, EffectPointTextHelper.getDescription(pointKey))));
    }

    private static MutableComponent withHover(MutableComponent line, Component hover) {
        return line.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static MutableComponent withKindLabel(String kind, MutableComponent body) {
        return Component.translatable("message.organeffects.effects.tooltip.kind." + kind)
                .withStyle(LABEL_COLOR)
                .append(Component.literal(" ").withStyle(SEPARATOR_COLOR))
                .append(body);
    }

    private static MutableComponent resolveCustomDisplay(String translationKey) {
        if (translationKey == null || translationKey.isBlank()) {
            return null;
        }
        MutableComponent translated = Component.translatable(translationKey);
        return translated.getString().equals(translationKey) ? Component.literal(translationKey) : translated;
    }

    private static MutableComponent joinComponents(List<Component> parts) {
        MutableComponent combined = Component.empty();
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) {
                combined.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            combined.append(parts.get(index));
        }
        return combined;
    }

    private static String joinPlain(List<Component> parts) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(parts.get(index).getString());
        }
        return builder.toString();
    }

    private static MutableComponent translateKey(String prefix, String value) {
        if (value == null || value.isBlank()) {
            return Component.translatable("message.organeffects.effects.unknown");
        }
        String key = prefix + sanitizeSegment(value);
        MutableComponent translated = Component.translatable(key);
        return translated.getString().equals(key) ? Component.literal(value) : translated;
    }

    private static Component translateScope(String scope) {
        return translateKey("message.organeffects.effects.scope.", scope);
    }

    private static Component formatOperator(String operator) {
        return translateKey("message.organeffects.effects.operator.", operator);
    }

    private static Component describeItemId(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            return Component.literal(rawId);
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item != null ? item.getDescription() : Component.literal(rawId);
    }

    private static Component describeBlockId(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            return Component.literal(rawId);
        }
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        return block != null ? block.getName() : Component.literal(rawId);
    }

    private static Component describeBiomeId(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        return id != null ? Component.literal(id.toString()) : Component.literal(rawId);
    }

    private static Component describeAttributeId(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        return id != null ? Component.literal(id.toString()) : Component.literal(rawId == null ? "?" : rawId);
    }

    private static Component describeEntityId(String rawId, String tag) {
        if (rawId != null) {
            return describeBiomeId(rawId);
        }
        if (tag != null) {
            return Component.literal(tag);
        }
        return Component.translatable("message.organeffects.effects.unknown");
    }

    private static Component describeEquipmentCondition(EffectDefinition.Condition condition) {
        if (condition.configBoolean("empty") != null) {
            return Component.translatable("message.organeffects.effects.condition.equipment_empty",
                    translateKey("message.organeffects.effects.slot.", condition.configString("slot")),
                    condition.configBoolean("empty"));
        }
        if (condition.configString("item") != null) {
            return Component.translatable("message.organeffects.effects.condition.equipment_item",
                    translateKey("message.organeffects.effects.slot.", condition.configString("slot")),
                    describeItemId(condition.configString("item")));
        }
        if (condition.configString("item_tag") != null) {
            return Component.translatable("message.organeffects.effects.condition.equipment_tag",
                    translateKey("message.organeffects.effects.slot.", condition.configString("slot")),
                    condition.configString("item_tag"));
        }
        return Component.translatable("message.organeffects.effects.condition.equipment_any",
                translateKey("message.organeffects.effects.slot.", condition.configString("slot")));
    }

    private static Component describeThresholdCondition(String key, String label, String operator, Long value, Long min, Long max) {
        if (min != null || max != null) {
            return Component.translatable(key + ".range", translateKey("message.organeffects.effects.mode.", label), valueOf(min), valueOf(max));
        }
        return Component.translatable(key + ".value", translateKey("message.organeffects.effects.mode.", label), formatOperator(operator), valueOf(value));
    }

    private static Component describeTeleportAction(EffectDefinition.BonusAction action) {
        if (action.configDouble("dx") != null || action.configDouble("dy") != null || action.configDouble("dz") != null) {
            return Component.translatable("message.organeffects.effects.action.teleport_offset",
                    valueOf(action.configDouble("dx")), valueOf(action.configDouble("dy")), valueOf(action.configDouble("dz")));
        }
        return Component.translatable("message.organeffects.effects.action.teleport_absolute",
                valueOf(action.configDouble("x")), valueOf(action.configDouble("y")), valueOf(action.configDouble("z")));
    }

    private static Component describeDimensionId(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        return id != null ? Component.literal(id.toString()) : Component.literal(rawId);
    }

    private static Component describeMobEffect(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            return Component.literal(rawId);
        }
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
        return effect != null ? Component.translatable(effect.getDescriptionId()) : Component.literal(rawId);
    }

    private static Component describeOrganId(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        return id != null ? Component.literal(id.toString()) : Component.literal(rawId);
    }

    private static String sanitizeSegment(String value) {
        return value.toLowerCase(Locale.ROOT).replace(':', '.').replace('/', '.').replace(' ', '_');
    }

    private static String formatNumber(Number value) {
        if (value == null) {
            return "0";
        }
        double doubleValue = value.doubleValue();
        if (Math.floor(doubleValue) == doubleValue) {
            return Long.toString(value.longValue());
        }
        return Double.toString(doubleValue);
    }

    private static Component valueOf(Number value) {
        return Component.literal(value == null ? "?" : formatNumber(value));
    }

    private record DisplayLine(String kind, MutableComponent text, Component hover) {
    }

    private record ViewerEffectEntry(String kind, Component text, String amountText, Component hover) {
    }

    private static final class MergedViewerEntry {
        private final String kind;
        private final Component text;
        private final Set<String> hoverStrings = new LinkedHashSet<>();
        private int occurrenceCount = 0;
        private int amountCount = 0;
        private String amountText;

        private MergedViewerEntry(String kind, Component text) {
            this.kind = kind;
            this.text = text;
        }

        private void add(Component hover, String amountText) {
            occurrenceCount++;
            if (hover != null && !hover.getString().isBlank()) {
                hoverStrings.add(hover.getString());
            }
            if (amountText != null && !amountText.isBlank()) {
                this.amountText = amountText;
                this.amountCount++;
            }
        }

        private MutableComponent renderText() {
            MutableComponent rendered = text.copy();
            if (amountText == null) {
                if (occurrenceCount > 1) {
                    return rendered.append(Component.literal(" x" + occurrenceCount).withStyle(ChatFormatting.GOLD));
                }
                return rendered;
            }
            if (amountCount <= 1) {
                rendered.append(Component.literal(" " + amountText).withStyle(ChatFormatting.GOLD));
                if (occurrenceCount > 1) {
                    rendered.append(Component.literal(" x" + occurrenceCount).withStyle(ChatFormatting.GOLD));
                }
                return rendered;
            }
            return rendered.append(Component.literal(" " + amountText + " x" + amountCount).withStyle(ChatFormatting.GOLD));
        }

        private Component buildHover() {
            MutableComponent merged = Component.empty();
            int index = 0;
            for (String hover : hoverStrings) {
                if (index > 0) {
                    merged.append(Component.literal("\n---\n").withStyle(ChatFormatting.DARK_GRAY));
                }
                merged.append(Component.literal(hover));
                index++;
            }
            return merged;
        }
    }
}
