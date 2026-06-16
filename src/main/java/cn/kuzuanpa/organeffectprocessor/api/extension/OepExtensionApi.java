package cn.kuzuanpa.organeffectprocessor.api.extension;

import cn.kuzuanpa.organeffectprocessor.api.EffectDefinition;
import cn.kuzuanpa.organeffectprocessor.common.capability.IEffectHolder;
import cn.kuzuanpa.organeffectprocessor.common.debug.OepDebug;
import cn.kuzuanpa.organeffectprocessor.common.effect.EffectRecalculationService;
import com.google.gson.JsonElement;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

public final class OepExtensionApi {
    private static final Map<String, PointProducer> POINT_PRODUCERS = new LinkedHashMap<>();
    private static final Map<String, PointExecutor> POINT_EXECUTORS = new LinkedHashMap<>();
    private static final Map<String, ConditionHandler> CONDITION_HANDLERS = new LinkedHashMap<>();
    private static final Map<String, ConditionDisplayRenderer> CONDITION_DISPLAYS = new LinkedHashMap<>();
    private static final Map<String, EventDisplayRenderer> EVENT_DISPLAYS = new LinkedHashMap<>();
    private static final Map<String, ActionDisplayRenderer> ACTION_DISPLAYS = new LinkedHashMap<>();
    private static final Map<String, EventFilterHandler> EVENT_FILTER_HANDLERS = new LinkedHashMap<>();

    private OepExtensionApi() {
    }

    public static void registerPointProducer(PointProducer producer) {
        POINT_PRODUCERS.put(producer.id(), producer);
    }

    public static void registerPointExecutor(PointExecutor executor) {
        POINT_EXECUTORS.put(executor.type(), executor);
    }

    public static void registerConditionHandler(String type, ConditionHandler handler) {
        CONDITION_HANDLERS.put(type, handler);
    }

    public static void registerConditionDisplay(String type, ConditionDisplayRenderer renderer) {
        CONDITION_DISPLAYS.put(type, renderer);
    }

    public static void registerEventDisplay(String type, EventDisplayRenderer renderer) {
        EVENT_DISPLAYS.put(type, renderer);
    }

    public static void registerActionDisplay(String type, ActionDisplayRenderer renderer) {
        ACTION_DISPLAYS.put(type, renderer);
    }

    public static void registerEventFilter(String key, EventFilterHandler handler) {
        EVENT_FILTER_HANDLERS.put(key, handler);
    }

    public static Collection<PointProducer> getPointProducers() {
        return List.copyOf(POINT_PRODUCERS.values());
    }

    public static PointExecutor getPointExecutor(String type) {
        return POINT_EXECUTORS.get(type);
    }

    public static ConditionHandler getConditionHandler(String type) {
        return CONDITION_HANDLERS.get(type);
    }

    public static Component renderCondition(EffectDefinition.Condition condition) {
        ConditionDisplayRenderer renderer = CONDITION_DISPLAYS.get(condition.type());
        return renderer != null ? renderer.render(condition) : null;
    }

    public static Component renderEvent(EffectDefinition.EventRule event) {
        EventDisplayRenderer renderer = EVENT_DISPLAYS.get(event.type());
        return renderer != null ? renderer.render(event) : null;
    }

    public static Component renderAction(EffectDefinition.BonusAction action) {
        ActionDisplayRenderer renderer = ACTION_DISPLAYS.get(action.type());
        return renderer != null ? renderer.render(action) : null;
    }

    public static boolean matchesExtraEventFilters(EffectDefinition.EventRule eventRule, OepRuntimeEvent event) {
        for (Map.Entry<String, JsonElement> entry : eventRule.extra().entrySet()) {
            EventFilterHandler handler = EVENT_FILTER_HANDLERS.get(entry.getKey());
            if (handler == null) {
                continue;
            }
            if (!handler.matches(eventRule, event, entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    public static void registerBuiltins() {
        registerBuiltinConditions();
        registerBuiltinDisplays();
        registerPointExecutor(new GrantItemsExecutor());
        registerPointExecutor(new ApplyMobEffectExecutor());
        registerPointExecutor(new HealExecutor());
        registerPointExecutor(new TauntExecutor());
    }

    private static void registerBuiltinConditions() {
        registerConditionHandler("static", (context, condition) -> true);
        registerConditionHandler("slot_index", (context, condition) -> EffectRecalculationService.compareLong(context.position().slotIndex(), condition.operator(), condition.value()));
        registerConditionHandler("distance_to_edge", (context, condition) -> EffectRecalculationService.compareLong(
                context.evaluationContext().distanceToEdge(context.position(), condition.edge()),
                condition.operator(),
                condition.value()));
        registerConditionHandler("weather", (context, condition) -> {
            if (condition.weather() == null) {
                return false;
            }
            return switch (condition.weather()) {
                case "clear" -> !context.evaluationContext().entity().level().isRaining() && !context.evaluationContext().entity().level().isThundering();
                case "rain" -> context.evaluationContext().entity().level().isRaining() && !context.evaluationContext().entity().level().isThundering();
                case "thunder" -> context.evaluationContext().entity().level().isThundering();
                default -> false;
            };
        });
        registerConditionHandler("time", (context, condition) -> {
            if (condition.time() != null) {
                return switch (condition.time()) {
                    case "day" -> context.evaluationContext().entity().level().isDay();
                    case "night" -> !context.evaluationContext().entity().level().isDay();
                    default -> false;
                };
            }
            long timeOfDay = Math.floorMod(context.evaluationContext().entity().level().getDayTime(), EffectRecalculationService.dayTicks());
            if (condition.min() != null || condition.max() != null) {
                long min = condition.min() != null ? condition.min() : 0L;
                long max = condition.max() != null ? condition.max() : EffectRecalculationService.dayTicks() - 1L;
                if (min <= max) {
                    return timeOfDay >= min && timeOfDay <= max;
                }
                return timeOfDay >= min || timeOfDay <= max;
            }
            return EffectRecalculationService.compareLong(timeOfDay, condition.operator(), condition.value());
        });
        registerConditionHandler("has_organ", (context, condition) -> {
            if (condition.scope() == null || condition.organ() == null) {
                return false;
            }
            ResourceLocation organId = ResourceLocation.tryParse(condition.organ());
            if (organId == null) {
                return false;
            }
            return switch (condition.scope()) {
                case "whole_body" -> context.evaluationContext().organCount(organId) > 0;
                case "body_part" -> {
                    ResourceLocation bodyPartId = ResourceLocation.tryParse(condition.bodyPart());
                    yield bodyPartId != null && context.evaluationContext().hasOrganInBodyPart(bodyPartId, organId);
                }
                case "exact_position" -> {
                    ResourceLocation bodyPartId = condition.bodyPart() != null ? ResourceLocation.tryParse(condition.bodyPart()) : context.position().bodyPartId();
                    Integer slot = condition.slot();
                    yield bodyPartId != null && slot != null && organId.equals(context.evaluationContext().organAt(bodyPartId, slot));
                }
                case "symmetric_position" -> context.evaluationContext().symmetricBodyPart(context.position().bodyPartId())
                        .map(bodyPartId -> organId.equals(context.evaluationContext().organAt(bodyPartId, context.position().slotIndex())))
                        .orElse(false);
                default -> false;
            };
        });
        registerConditionHandler("biome", (context, condition) -> {
            net.minecraft.core.Holder<Biome> biomeHolder = context.evaluationContext().biome();
            if (condition.biome() != null) {
                ResourceLocation biomeId = ResourceLocation.tryParse(condition.biome());
                if (biomeId == null || !biomeHolder.is(ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, biomeId))) {
                    return false;
                }
            }
            if (condition.biomeTag() != null) {
                ResourceLocation biomeTagId = ResourceLocation.tryParse(condition.biomeTag());
                if (biomeTagId == null || !biomeHolder.is(TagKey.create(net.minecraft.core.registries.Registries.BIOME, biomeTagId))) {
                    return false;
                }
            }
            return condition.biome() != null || condition.biomeTag() != null;
        });
        registerConditionHandler("dimid", (context, condition) -> {
            if (condition.dimension() == null) {
                return false;
            }
            ResourceLocation dimensionId = context.evaluationContext().entity().level().dimension().location();
            return condition.dimension().equals(dimensionId.toString());
        });
        registerConditionHandler("lightlevel", (context, condition) -> {
            if (condition.value() == null || condition.operator() == null) {
                return false;
            }
            long lightLevel = context.evaluationContext().entity().level().getMaxLocalRawBrightness(context.evaluationContext().blockPos());
            return EffectRecalculationService.compareLong(lightLevel, condition.operator(), condition.value());
        });
        registerConditionHandler("stepon", (context, condition) -> {
            BlockState blockState = context.evaluationContext().steppedOnBlock();
            if (blockState == null) {
                return false;
            }
            if (condition.block() != null) {
                ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(blockState.getBlock());
                if (blockId == null || !condition.block().equals(blockId.toString())) {
                    return false;
                }
            }
            if (condition.blockTag() != null) {
                ResourceLocation blockTagId = ResourceLocation.tryParse(condition.blockTag());
                if (blockTagId == null || !blockState.is(TagKey.create(net.minecraft.core.registries.Registries.BLOCK, blockTagId))) {
                    return false;
                }
            }
            return condition.block() != null || condition.blockTag() != null;
        });
    }

    private static void registerBuiltinDisplays() {
        registerEventFilter("food_only", (eventRule, event, key, value) -> !value.getAsBoolean() || (!event.itemStack().isEmpty() && event.itemStack().isEdible()));
    }

    private static final class GrantItemsExecutor implements PointExecutor {
        @Override
        public String type() {
            return "grant_items";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L || action.items().isEmpty()) {
                return;
            }
            Player player = context.player();
            int rolls = Math.max(1, action.rolls());
            java.util.Set<Integer> usedIndexes = action.unique() ? new java.util.LinkedHashSet<>() : java.util.Set.of();
            for (int roll = 0; roll < rolls; roll++) {
                int index = pickItemIndex(action.items(), player.getRandom(), usedIndexes);
                if (index < 0) {
                    break;
                }
                if (action.unique()) {
                    usedIndexes.add(index);
                }
                EffectDefinition.ItemEntry entry = action.items().get(index);
                ResourceLocation itemId = ResourceLocation.tryParse(entry.itemId());
                if (itemId == null) {
                    continue;
                }
                Item item = ForgeRegistries.ITEMS.getValue(itemId);
                if (item == null) {
                    continue;
                }
                ItemStack stack = new ItemStack(item, Math.max(1, entry.count()));
                if (!player.addItem(stack) && action.dropIfFull()) {
                    player.drop(stack, false);
                }
            }
        }

        private int pickItemIndex(List<EffectDefinition.ItemEntry> items, net.minecraft.util.RandomSource random, java.util.Set<Integer> excludedIndexes) {
            List<Integer> candidates = new java.util.ArrayList<>();
            int totalWeight = 0;
            for (int index = 0; index < items.size(); index++) {
                if (excludedIndexes.contains(index)) {
                    continue;
                }
                int weight = Math.max(1, items.get(index).weight());
                candidates.add(index);
                totalWeight += weight;
            }
            if (totalWeight <= 0 || candidates.isEmpty()) {
                return -1;
            }
            int ticket = random.nextInt(totalWeight);
            for (int index : candidates) {
                ticket -= Math.max(1, items.get(index).weight());
                if (ticket < 0) {
                    return index;
                }
            }
            return candidates.get(candidates.size() - 1);
        }
    }

    private static final class ApplyMobEffectExecutor implements PointExecutor {
        @Override
        public String type() {
            return "apply_mob_effect";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            if (action.effectId() == null) {
                OepDebug.trace(context.player(), "apply effect skipped missing effect id");
                return;
            }
            ResourceLocation effectId = ResourceLocation.tryParse(action.effectId());
            if (effectId == null) {
                OepDebug.trace(context.player(), "apply effect skipped invalid id=%s", action.effectId());
                return;
            }
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(effectId);
            if (effect == null) {
                OepDebug.trace(context.player(), "apply effect skipped registry miss=%s", effectId);
                return;
            }
            int duration = action.durationTicks() != null ? Math.max(2, action.durationTicks()) : 40;
            int amplifier = action.amplifier() != null ? Math.max(0, action.amplifier()) : 0;
            MobEffectInstance current = context.player().getEffect(effect);
            int refreshThreshold = Math.max(10, duration / 4);
            if (current != null && current.getAmplifier() >= amplifier && current.getDuration() > refreshThreshold) {
                return;
            }
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                OepDebug.trace(context.player(), "apply effect skipped zero usage id=%s", effectId);
                return;
            }
            boolean applied = context.player().addEffect(new MobEffectInstance(effect, duration, amplifier, false, true, true));
            MobEffectInstance after = context.player().getEffect(effect);
            OepDebug.trace(context.player(), "apply effect id=%s applied=%s used=%d finalDur=%d finalAmp=%d", effectId, applied, usage.usedPoints(), after != null ? after.getDuration() : 0, after != null ? after.getAmplifier() : -1);
        }
    }

    private static final class HealExecutor implements PointExecutor {
        @Override
        public String type() {
            return "heal";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            if (context.player().getHealth() >= context.player().getMaxHealth()) {
                return;
            }
            PointUsage preview = context.resolveUsage(action);
            if (preview.usedPoints() <= 0L) {
                return;
            }
            PointUsage usage = cn.kuzuanpa.organeffectprocessor.common.effect.RuntimePointExecutor.consumePointUsage(context.player(), context.holder(), action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            double total = action.amount() != null ? action.amount() : 0.0D;
            if (total > 0.0D) {
                context.player().heal((float) total);
            }
        }
    }

    private static final class TauntExecutor implements PointExecutor {
        @Override
        public String type() {
            return "taunt";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            Player player = context.player();
            double radius = action.amount() != null ? Math.max(1.0D, action.amount()) : 8.0D;
            AABB area = player.getBoundingBox().inflate(radius);
            List<Mob> mobs = player.level().getEntitiesOfClass(Mob.class, area, mob -> mob instanceof Enemy
                    && mob.isAlive()
                    && EntitySelector.NO_SPECTATORS.test(mob)
                    && mob.canAttack(player));
            for (Mob mob : mobs) {
                if (!"hostile".equals(action.target()) && action.target() != null && !action.target().isBlank()) {
                    continue;
                }
                if (!mob.getSensing().hasLineOfSight(player) && !TargetingConditions.DEFAULT.test(mob, player)) {
                    continue;
                }
                mob.setTarget(player);
                mob.setLastHurtByMob(player);
            }
        }
    }
}
