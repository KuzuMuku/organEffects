package cn.kuzuanpa.organeffectprocessor.common.point;

import cn.kuzuanpa.organapi.api.organ.OrganDefinition;
import cn.kuzuanpa.organapi.api.query.OrganPosition;
import cn.kuzuanpa.organapi.common.data.OrganRegistryAccess;
import cn.kuzuanpa.organeffectprocessor.api.EffectDefinition;
import cn.kuzuanpa.organeffectprocessor.api.extension.OepExtensionApi;
import cn.kuzuanpa.organeffectprocessor.common.data.OrganEffectData;
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
                appendViewerGrantEntries(entries, definition, position, effectIndex, effect.conditions(), effect.grants());
                appendViewerEventEntries(entries, definition, position, effectIndex, effect.conditions(), effect.events());
                appendViewerExecutionEntries(entries, definition, position, effectIndex, effect.conditions(), effect.executions());
                effectIndex++;
            }
        }
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<String, MergedViewerEntry> merged = new LinkedHashMap<>();
        for (ViewerEffectEntry entry : entries) {
            merged.computeIfAbsent(entry.kind() + "\n" + entry.text(), key -> new MergedViewerEntry(entry.kind(), entry.text()))
                    .add(entry.hover(), entry.amountText());
        }
        List<Component> lines = new ArrayList<>();
        for (MergedViewerEntry entry : merged.values()) {
            lines.add(withHover(Component.literal(entry.renderText()), entry.buildHover()));
        }
        return lines;
    }

    public static List<Component> buildTooltipLines(OrganDefinition definition, List<EffectDefinition> effects, boolean expanded) {
        List<Component> allLines = new ArrayList<>();
        for (String tooltip : definition.tooltips()) {
            allLines.add(Component.literal(tooltip).withStyle(ChatFormatting.GRAY));
        }

        List<DisplayLine> effectDisplayLines = new ArrayList<>();
        for (EffectDefinition effect : effects) {
            appendGrantLines(effectDisplayLines, effect.conditions(), effect.grants(), false);
            appendEventLines(effectDisplayLines, effect.conditions(), effect.events());
            appendExecutionLines(effectDisplayLines, effect.conditions(), effect.executions());
        }
        if (effectDisplayLines.isEmpty()) {
            return allLines;
        }

        List<Component> effectLines = new ArrayList<>();
        for (DisplayLine line : effectDisplayLines) {
            effectLines.add(withHover(line.text().copy(), line.hover()));
        }

        allLines.add(Component.translatable("message.organeffectprocessor.effects.tooltip.header").withStyle(ChatFormatting.AQUA));
        if (expanded) {
            allLines.addAll(effectLines);
            allLines.add(Component.translatable("message.organeffectprocessor.effects.tooltip.expanded").withStyle(ChatFormatting.DARK_GRAY));
            return allLines;
        }

        int visibleCount = Math.min(DEFAULT_TOOLTIP_LINES, effectLines.size());
        for (int index = 0; index < visibleCount; index++) {
            allLines.add(effectLines.get(index));
        }
        if (effectLines.size() > visibleCount) {
            allLines.add(Component.translatable("message.organeffectprocessor.effects.tooltip.more", effectLines.size() - visibleCount)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        allLines.add(Component.translatable("message.organeffectprocessor.effects.tooltip.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
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
            Component point = formatPointAmount(grant.type(), grant.id(), grant.amount());
            target.add(new DisplayLine("grant",
                    prefix.copy().append(Component.translatable("message.organeffectprocessor.effects.provides", point)),
                    buildConditionHover(conditions)));
        }
    }

    private static void appendEventLines(List<DisplayLine> target, List<EffectDefinition.Condition> conditions, List<EffectDefinition.EventRule> events) {
        for (EffectDefinition.EventRule event : events) {
            List<Component> pieces = new ArrayList<>();
            for (EffectDefinition.PointMutation mutation : event.addPoints()) {
                pieces.add(Component.translatable("message.organeffectprocessor.effects.gains",
                        formatPointAmount(mutation.type(), mutation.id(), mutation.amount())));
            }
            for (EffectDefinition.PointMutation mutation : event.consumePoints()) {
                pieces.add(Component.translatable("message.organeffectprocessor.effects.consumes",
                        formatPointAmount(mutation.type(), mutation.id(), mutation.amount())));
            }
            for (EffectDefinition.BonusAction action : event.actions()) {
                pieces.add(describeAction(action));
            }
            if (pieces.isEmpty()) {
                continue;
            }
            MutableComponent prefix = describeConditionPrefix(conditions, false)
                    .append(Component.translatable("message.organeffectprocessor.effects.on_event", describeEvent(event)).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
            Component body = joinComponents(pieces);
            target.add(new DisplayLine("event", prefix.append(body), buildEventHover(conditions, event)));
        }
    }

    private static void appendExecutionLines(List<DisplayLine> target, List<EffectDefinition.Condition> conditions, List<EffectDefinition.BonusAction> executions) {
        for (EffectDefinition.BonusAction execution : executions) {
            MutableComponent prefix = describeConditionPrefix(conditions, false)
                    .append(Component.translatable("message.organeffectprocessor.effects.when_points", describePointBinding(execution)).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
            target.add(new DisplayLine("execution", prefix.append(describeAction(execution)), buildExecutionHover(conditions, execution)));
        }
    }

    private static void appendViewerGrantEntries(List<ViewerEffectEntry> target, OrganDefinition definition, OrganPosition position, int effectIndex,
                                                 List<EffectDefinition.Condition> conditions, List<EffectDefinition.Grant> grants) {
        if (conditions.isEmpty() || conditions.stream().allMatch(condition -> condition == null || Objects.equals(condition.type(), "static"))) {
            return;
        }
        for (EffectDefinition.Grant grant : grants) {
            String text = describeConditionPrefix(conditions, false).getString() + Component.translatable("message.organeffectprocessor.effects.provides",
                    EffectPointTextHelper.getDisplayName(grant.type() + ":" + grant.id())).getString();
            String amountText = "+" + grant.amount();
            target.add(new ViewerEffectEntry("grant", text, amountText, buildViewerHover(definition, position, effectIndex, conditions, null, null)));
        }
    }

    private static void appendViewerEventEntries(List<ViewerEffectEntry> target, OrganDefinition definition, OrganPosition position, int effectIndex,
                                                 List<EffectDefinition.Condition> conditions, List<EffectDefinition.EventRule> events) {
        for (EffectDefinition.EventRule event : events) {
            for (EffectDefinition.PointMutation mutation : event.addPoints()) {
                String text = Component.translatable("message.organeffectprocessor.effects.on_event", describeEvent(event)).getString()
                        + ": " + Component.translatable("message.organeffectprocessor.effects.gains",
                        EffectPointTextHelper.getDisplayName(mutation.type() + ":" + mutation.id())).getString();
                String amountText = "+" + mutation.amount();
                target.add(new ViewerEffectEntry("event", text, amountText, buildViewerHover(definition, position, effectIndex, conditions, event, null)));
            }
        }
    }

    private static void appendViewerExecutionEntries(List<ViewerEffectEntry> target, OrganDefinition definition, OrganPosition position, int effectIndex,
                                                     List<EffectDefinition.Condition> conditions, List<EffectDefinition.BonusAction> executions) {
        for (EffectDefinition.BonusAction execution : executions) {
            String text = Component.translatable("message.organeffectprocessor.effects.when_points", describePointBinding(execution)).getString()
                    + ": " + describeAction(execution).getString();
            target.add(new ViewerEffectEntry("execution", text, null, buildViewerHover(definition, position, effectIndex, conditions, null, execution)));
        }
    }

    private static MutableComponent describeConditionPrefix(List<EffectDefinition.Condition> conditions, boolean includeAlways) {
        List<Component> readableConditions = describeConditions(conditions);
        if (readableConditions.isEmpty()) {
            return includeAlways
                    ? Component.translatable("message.organeffectprocessor.effects.always_prefix").withStyle(ChatFormatting.YELLOW)
                    : Component.empty();
        }
        return Component.translatable("message.organeffectprocessor.effects.when_prefix", joinComponents(readableConditions))
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
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
        Component custom = OepExtensionApi.renderCondition(condition);
        if (custom != null) {
            return custom;
        }
        return switch (condition.type()) {
            case "health" -> describeThresholdCondition("message.organeffectprocessor.effects.condition.health", condition.configString("mode"), condition.configString("op"),
                    condition.configLong("value"), condition.configLong("min"), condition.configLong("max"));
            case "hunger" -> describeThresholdCondition("message.organeffectprocessor.effects.condition.hunger", condition.configString("mode"), condition.configString("op"),
                    condition.configLong("value"), condition.configLong("min"), condition.configLong("max"));
            case "air" -> "underwater".equals(condition.configString("mode"))
                    ? Component.translatable("message.organeffectprocessor.effects.condition.air_underwater")
                    : describeThresholdCondition("message.organeffectprocessor.effects.condition.air", condition.configString("mode"), condition.configString("op"),
                    condition.configLong("value"), null, null);
            case "xp" -> describeThresholdCondition("message.organeffectprocessor.effects.condition.xp", condition.configString("mode"), condition.configString("op"),
                    condition.configLong("value"), condition.configLong("min"), condition.configLong("max"));
            case "status_effect" -> Component.translatable("message.organeffectprocessor.effects.condition.status_effect",
                    describeMobEffect(condition.configString("effect")));
            case "attribute" -> describeThresholdCondition("message.organeffectprocessor.effects.condition.attribute",
                    describeAttributeId(condition.configString("attribute")).getString(), condition.configString("op"), condition.configLong("value"),
                    condition.configLong("min"), condition.configLong("max"));
            case "movement_state" -> Component.translatable("message.organeffectprocessor.effects.condition.movement_state",
                    translateKey("message.organeffectprocessor.effects.state.", condition.configString("state")));
            case "environment_state" -> Component.translatable("message.organeffectprocessor.effects.condition.environment_state",
                    translateKey("message.organeffectprocessor.effects.state.", condition.configString("state")));
            case "equipment" -> describeEquipmentCondition(condition);
            case "enchantment" -> Component.translatable("message.organeffectprocessor.effects.condition.enchantment",
                    translateKey("message.organeffectprocessor.effects.slot.", condition.configString("slot")),
                    describeItemId(condition.configString("enchantment")),
                    formatOperator(condition.configString("op")), valueOf(condition.configLong("value")));
            case "nearby_entity" -> describeThresholdCondition("message.organeffectprocessor.effects.condition.nearby_entity",
                    describeEntityId(condition.configString("entity"), condition.configString("entity_tag")).getString(),
                    condition.configString("op"), condition.configLong("value"), condition.configLong("min"), condition.configLong("max"));
            case "moon_phase" -> condition.configString("mode") != null
                    ? Component.translatable("message.organeffectprocessor.effects.condition.moon_phase_mode",
                    translateKey("message.organeffectprocessor.effects.moon_phase.", condition.configString("mode")))
                    : Component.translatable("message.organeffectprocessor.effects.condition.moon_phase_value",
                    formatOperator(condition.configString("op")), valueOf(condition.configLong("value")));
            case "biome_category" -> Component.translatable("message.organeffectprocessor.effects.condition.biome_category",
                    condition.configString("value"));
            case "dimension_type" -> Component.translatable("message.organeffectprocessor.effects.condition.dimension_type",
                    describeDimensionId(condition.configString("value")));
            case "slot_index" -> Component.translatable("message.organeffectprocessor.effects.condition.slot_index",
                    formatOperator(condition.operator()), valueOf(condition.value()));
            case "distance_to_edge" -> Component.translatable("message.organeffectprocessor.effects.condition.distance_to_edge",
                    translateKey("message.organeffectprocessor.effects.edge.", condition.edge()),
                    formatOperator(condition.operator()), valueOf(condition.value()));
            case "weather" -> Component.translatable("message.organeffectprocessor.effects.condition.weather",
                    translateKey("message.organeffectprocessor.effects.weather.", condition.weather()));
            case "time" -> describeTimeCondition(condition);
            case "has_organ" -> Component.translatable("message.organeffectprocessor.effects.condition.has_organ",
                    translateScope(condition.scope()), describeOrganId(condition.organ()));
            case "biome" -> describeBiomeCondition(condition);
            case "dimid" -> Component.translatable("message.organeffectprocessor.effects.condition.dimid", describeDimensionId(condition.dimension()));
            case "lightlevel" -> Component.translatable("message.organeffectprocessor.effects.condition.lightlevel",
                    formatOperator(condition.operator()), valueOf(condition.value()));
            case "stepon" -> describeStepOnCondition(condition);
            default -> Component.literal(condition.type());
        };
    }

    private static Component describeBiomeCondition(EffectDefinition.Condition condition) {
        if (condition.biome() != null && condition.biomeTag() != null) {
            return Component.translatable("message.organeffectprocessor.effects.condition.biome_and_tag",
                    describeBiomeId(condition.biome()), condition.biomeTag());
        }
        if (condition.biomeTag() != null) {
            return Component.translatable("message.organeffectprocessor.effects.condition.biome_tag", condition.biomeTag());
        }
        return Component.translatable("message.organeffectprocessor.effects.condition.biome", describeBiomeId(condition.biome()));
    }

    private static Component describeStepOnCondition(EffectDefinition.Condition condition) {
        if (condition.block() != null && condition.blockTag() != null) {
            return Component.translatable("message.organeffectprocessor.effects.condition.stepon_and_tag",
                    describeBlockId(condition.block()), condition.blockTag());
        }
        if (condition.blockTag() != null) {
            return Component.translatable("message.organeffectprocessor.effects.condition.stepon_tag", condition.blockTag());
        }
        return Component.translatable("message.organeffectprocessor.effects.condition.stepon", describeBlockId(condition.block()));
    }

    private static Component describeTimeCondition(EffectDefinition.Condition condition) {
        if (condition.time() != null) {
            return Component.translatable("message.organeffectprocessor.effects.condition.time_mode",
                    translateKey("message.organeffectprocessor.effects.time.", condition.time()));
        }
        if (condition.min() != null || condition.max() != null) {
            return Component.translatable("message.organeffectprocessor.effects.condition.time_range",
                    valueOf(condition.min()), valueOf(condition.max()));
        }
        return Component.translatable("message.organeffectprocessor.effects.condition.time_value",
                formatOperator(condition.operator()), valueOf(condition.value()));
    }

    private static Component describeEvent(EffectDefinition.EventRule event) {
        Component custom = OepExtensionApi.renderEvent(event);
        if (custom != null) {
            return custom;
        }
        MutableComponent eventName = translateKey("message.organeffectprocessor.effects.event.", event.type());
        List<Component> filters = new ArrayList<>();
        if (event.foodOnly()) {
            filters.add(Component.translatable("message.organeffectprocessor.effects.event.filter.food_only"));
        }
        if (event.item() != null) {
            filters.add(Component.translatable("message.organeffectprocessor.effects.event.filter.item", describeItemId(event.item())));
        }
        if (event.itemTag() != null) {
            filters.add(Component.translatable("message.organeffectprocessor.effects.event.filter.item_tag", event.itemTag()));
        }
        if (event.block() != null) {
            filters.add(Component.translatable("message.organeffectprocessor.effects.event.filter.block", describeBlockId(event.block())));
        }
        if (event.blockTag() != null) {
            filters.add(Component.translatable("message.organeffectprocessor.effects.event.filter.block_tag", event.blockTag()));
        }
        if (event.distance() != null) {
            filters.add(Component.translatable("message.organeffectprocessor.effects.event.filter.distance", event.distance()));
        }
        if ("tick".equals(event.type())) {
            Long intervalTicks = event.configLong("interval_ticks");
            if (intervalTicks == null) {
                intervalTicks = event.configLong("interval");
            }
            if (intervalTicks != null && intervalTicks > 1L) {
                filters.add(Component.translatable("message.organeffectprocessor.effects.event.filter.interval_ticks", intervalTicks));
            }
        }
        if (filters.isEmpty()) {
            return eventName;
        }
        return eventName.append(Component.literal(" (" + joinPlain(filters) + ")"));
    }

    private static Component describeAction(EffectDefinition.BonusAction action) {
        Component custom = OepExtensionApi.renderAction(action);
        if (custom != null) {
            return custom;
        }
        return switch (action.type()) {
            case "damage_self" -> Component.translatable("message.organeffectprocessor.effects.action.damage_self", valueOf(action.amount()));
            case "damage_target" -> Component.translatable("message.organeffectprocessor.effects.action.damage_target", valueOf(action.amount()));
            case "knockback" -> Component.translatable("message.organeffectprocessor.effects.action.knockback", valueOf(action.amount()));
            case "launch" -> Component.translatable("message.organeffectprocessor.effects.action.launch", valueOf(action.configDouble("y")));
            case "teleport" -> describeTeleportAction(action);
            case "spawn_particle" -> Component.translatable("message.organeffectprocessor.effects.action.spawn_particle",
                    translateKey("message.organeffectprocessor.effects.unknown.", action.configString("particle")));
            case "play_sound" -> Component.translatable("message.organeffectprocessor.effects.action.play_sound",
                    translateKey("message.organeffectprocessor.effects.unknown.", action.configString("sound")));
            case "remove_effect" -> Component.translatable("message.organeffectprocessor.effects.action.remove_effect", describeMobEffect(action.effectId()));
            case "clear_negative_effects" -> Component.translatable("message.organeffectprocessor.effects.action.clear_negative_effects");
            case "give_xp" -> Component.translatable("message.organeffectprocessor.effects.action.give_xp", valueOf(action.amount()));
            case "consume_hunger" -> Component.translatable("message.organeffectprocessor.effects.action.consume_hunger", valueOf(action.amount()));
            case "restore_air" -> Component.translatable("message.organeffectprocessor.effects.action.restore_air", valueOf(action.amount()));
            case "set_fire" -> Component.translatable("message.organeffectprocessor.effects.action.set_fire", valueOf(action.amount()));
            case "extinguish" -> Component.translatable("message.organeffectprocessor.effects.action.extinguish");
            case "summon_entity" -> Component.translatable("message.organeffectprocessor.effects.action.summon_entity",
                    describeEntityId(action.configString("entity"), null));
            case "drop_items" -> Component.translatable("message.organeffectprocessor.effects.action.drop_items", valueOf(action.items().size()));
            case "set_cooldown" -> Component.translatable("message.organeffectprocessor.effects.action.set_cooldown", valueOf(action.durationTicks() != null ? action.durationTicks() : action.amount()));
            case "consume_item" -> Component.translatable("message.organeffectprocessor.effects.action.consume_item", valueOf(action.amount()));
            case "repair_item" -> Component.translatable("message.organeffectprocessor.effects.action.repair_item", valueOf(action.amount()));
            case "place_block" -> Component.translatable("message.organeffectprocessor.effects.action.place_block",
                    describeBlockId(action.configString("block")));
            case "convert_block" -> Component.translatable("message.organeffectprocessor.effects.action.convert_block",
                    describeBlockId(action.configString("from")), describeBlockId(action.configString("to")));
            case "force_target" -> Component.translatable("message.organeffectprocessor.effects.action.force_target", valueOf(action.amount()));
            case "apply_mob_effect" -> Component.translatable("message.organeffectprocessor.effects.action.apply_mob_effect",
                    describeMobEffect(action.effectId()), valueOf(action.durationTicks()), valueOf(action.amplifier()));
            case "heal" -> Component.translatable("message.organeffectprocessor.effects.action.heal", valueOf(action.amount()));
            case "grant_items" -> Component.translatable("message.organeffectprocessor.effects.action.grant_items",
                    valueOf(action.rolls()), valueOf(action.items().size()));
            case "taunt" -> Component.translatable("message.organeffectprocessor.effects.action.taunt",
                    translateKey("message.organeffectprocessor.effects.target.", action.target()),
                    valueOf(action.amount() != null ? action.amount() : 8.0D),
                    valueOf(action.durationTicks() != null && action.durationTicks() > 0 ? action.durationTicks() : 60));
            default -> Component.literal(action.type());
        };
    }

    private static Component describePointBinding(EffectDefinition.BonusAction action) {
        if (action.pointType() == null || action.pointId() == null) {
            return Component.translatable("message.organeffectprocessor.effects.unknown");
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
            return Component.translatable("message.organeffectprocessor.effects.hover.always");
        }
        return Component.translatable("message.organeffectprocessor.effects.hover.conditions", joinComponents(readableConditions));
    }

    private static Component buildEventHover(List<EffectDefinition.Condition> conditions, EffectDefinition.EventRule event) {
        MutableComponent hover = Component.empty().append(buildConditionHover(conditions));
        if (event.source() != null) {
            hover.append(Component.literal("\n")).append(Component.translatable("message.organeffectprocessor.effects.hover.source", event.source()));
        }
        return hover;
    }

    private static Component buildExecutionHover(List<EffectDefinition.Condition> conditions, EffectDefinition.BonusAction execution) {
        MutableComponent hover = Component.empty().append(buildConditionHover(conditions));
        if (execution.isPointsConsume()) {
            hover.append(Component.literal("\n")).append(Component.translatable("message.organeffectprocessor.effects.hover.consume_points"));
        }
        if (execution.source() != null) {
            hover.append(Component.literal("\n")).append(Component.translatable("message.organeffectprocessor.effects.hover.source", execution.source()));
        }
        return hover;
    }

    private static Component buildViewerHover(OrganDefinition definition, OrganPosition position, int effectIndex,
                                              List<EffectDefinition.Condition> conditions, EffectDefinition.EventRule event,
                                              EffectDefinition.BonusAction execution) {
        MutableComponent hover = Component.empty()
                .append(getOrganHeader(definition))
                .append(Component.literal("\n"))
                .append(Component.literal(position.bodyPartId() + " #" + position.slotIndex()).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("\n"))
                .append(Component.literal("effect #" + effectIndex).withStyle(ChatFormatting.DARK_GRAY));
        Component conditionHover = buildConditionHover(conditions);
        if (!conditionHover.getString().isBlank()) {
            hover.append(Component.literal("\n")).append(conditionHover);
        }
        if (event != null && event.source() != null) {
            hover.append(Component.literal("\n")).append(Component.translatable("message.organeffectprocessor.effects.hover.source", event.source()));
        }
        if (execution != null) {
            if (execution.isPointsConsume()) {
                hover.append(Component.literal("\n")).append(Component.translatable("message.organeffectprocessor.effects.hover.consume_points"));
            }
            if (execution.source() != null) {
                hover.append(Component.literal("\n")).append(Component.translatable("message.organeffectprocessor.effects.hover.source", execution.source()));
            }
        }
        return hover;
    }

    private static Component formatPointAmount(String type, String id, Number amount) {
        String pointKey = type + ":" + id;
        return EffectPointTextHelper.getDisplayName(pointKey)
                .copy()
                .append(Component.literal(" +" + formatNumber(amount)).withStyle(ChatFormatting.GOLD));
    }

    private static MutableComponent withHover(MutableComponent line, Component hover) {
        return line.withStyle(style -> style.withColor(ChatFormatting.GREEN)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
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
            return Component.translatable("message.organeffectprocessor.effects.unknown");
        }
        String key = prefix + sanitizeSegment(value);
        MutableComponent translated = Component.translatable(key);
        return translated.getString().equals(key) ? Component.literal(value) : translated;
    }

    private static Component translateScope(String scope) {
        return translateKey("message.organeffectprocessor.effects.scope.", scope);
    }

    private static Component formatOperator(String operator) {
        return translateKey("message.organeffectprocessor.effects.operator.", operator);
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
        return Component.translatable("message.organeffectprocessor.effects.unknown");
    }

    private static Component describeEquipmentCondition(EffectDefinition.Condition condition) {
        if (condition.configBoolean("empty") != null) {
            return Component.translatable("message.organeffectprocessor.effects.condition.equipment_empty",
                    translateKey("message.organeffectprocessor.effects.slot.", condition.configString("slot")),
                    condition.configBoolean("empty"));
        }
        if (condition.configString("item") != null) {
            return Component.translatable("message.organeffectprocessor.effects.condition.equipment_item",
                    translateKey("message.organeffectprocessor.effects.slot.", condition.configString("slot")),
                    describeItemId(condition.configString("item")));
        }
        if (condition.configString("item_tag") != null) {
            return Component.translatable("message.organeffectprocessor.effects.condition.equipment_tag",
                    translateKey("message.organeffectprocessor.effects.slot.", condition.configString("slot")),
                    condition.configString("item_tag"));
        }
        return Component.translatable("message.organeffectprocessor.effects.condition.equipment_any",
                translateKey("message.organeffectprocessor.effects.slot.", condition.configString("slot")));
    }

    private static Component describeThresholdCondition(String key, String label, String operator, Long value, Long min, Long max) {
        if (min != null || max != null) {
            return Component.translatable(key + ".range", translateKey("message.organeffectprocessor.effects.mode.", label), valueOf(min), valueOf(max));
        }
        return Component.translatable(key + ".value", translateKey("message.organeffectprocessor.effects.mode.", label), formatOperator(operator), valueOf(value));
    }

    private static Component describeTeleportAction(EffectDefinition.BonusAction action) {
        if (action.configDouble("dx") != null || action.configDouble("dy") != null || action.configDouble("dz") != null) {
            return Component.translatable("message.organeffectprocessor.effects.action.teleport_offset",
                    valueOf(action.configDouble("dx")), valueOf(action.configDouble("dy")), valueOf(action.configDouble("dz")));
        }
        return Component.translatable("message.organeffectprocessor.effects.action.teleport_absolute",
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

    private record ViewerEffectEntry(String kind, String text, String amountText, Component hover) {
    }

    private static final class MergedViewerEntry {
        private final String kind;
        private final String text;
        private final Set<String> hoverStrings = new LinkedHashSet<>();
        private int amountCount = 0;
        private String amountText;

        private MergedViewerEntry(String kind, String text) {
            this.kind = kind;
            this.text = text;
        }

        private void add(Component hover, String amountText) {
            if (hover != null && !hover.getString().isBlank()) {
                hoverStrings.add(hover.getString());
            }
            if (amountText != null && !amountText.isBlank()) {
                this.amountText = amountText;
                this.amountCount++;
            }
        }

        private String renderText() {
            if (amountText == null) {
                return text;
            }
            if (amountCount <= 1) {
                return text + " " + amountText;
            }
            return text + " " + amountText + " x" + amountCount;
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
