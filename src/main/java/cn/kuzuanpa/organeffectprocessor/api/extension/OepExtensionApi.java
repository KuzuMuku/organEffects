package cn.kuzuanpa.organeffectprocessor.api.extension;

import cn.kuzuanpa.organeffectprocessor.api.EffectDefinition;
import cn.kuzuanpa.organeffectprocessor.common.capability.IEffectHolder;
import cn.kuzuanpa.organeffectprocessor.common.debug.OepDebug;
import cn.kuzuanpa.organeffectprocessor.common.effect.EffectRecalculationService;
import cn.kuzuanpa.organeffectprocessor.common.effect.RuntimeEffectService;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
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
        registerPointExecutor(new DamageSelfExecutor());
        registerPointExecutor(new TeleportExecutor());
        registerPointExecutor(new KnockbackExecutor());
        registerPointExecutor(new LaunchExecutor());
        registerPointExecutor(new GiveXpExecutor());
        registerPointExecutor(new ClearNegativeEffectsExecutor());
        registerPointExecutor(new SetFireExecutor());
        registerPointExecutor(new ExtinguishExecutor());
        registerPointExecutor(new SummonEntityExecutor());
        registerPointExecutor(new ForceTargetExecutor());
        registerPointExecutor(new PlaySoundExecutor());
        registerPointExecutor(new SpawnParticleExecutor());
        registerPointExecutor(new ConsumeHungerExecutor());
        registerPointExecutor(new RestoreAirExecutor());
        registerPointExecutor(new ConsumeItemExecutor());
        registerPointExecutor(new RepairItemExecutor());
        registerPointExecutor(new SetCooldownExecutor());
        registerPointExecutor(new RemoveEffectExecutor());
        registerPointExecutor(new DropItemsExecutor());
        registerPointExecutor(new DamageTargetExecutor());
        registerPointExecutor(new PlaceBlockExecutor());
        registerPointExecutor(new ConvertBlockExecutor());
    }

    private static void registerBuiltinConditions() {
        registerConditionHandler("static", (context, condition) -> true);
        registerConditionHandler("health", (context, condition) -> {
            if (!(context.evaluationContext().entity() instanceof net.minecraft.world.entity.LivingEntity living)) {
                return false;
            }
            String mode = condition.configString("mode");
            long current = switch (mode == null ? "value" : mode) {
                case "percent" -> Math.round((living.getHealth() / Math.max(1.0F, living.getMaxHealth())) * 100.0D);
                case "missing" -> Math.round(living.getMaxHealth() - living.getHealth());
                case "max" -> Math.round(living.getMaxHealth());
                default -> Math.round(living.getHealth());
            };
            Long min = condition.configLong("min");
            Long max = condition.configLong("max");
            if (min != null || max != null) {
                long actualMin = min != null ? min : Long.MIN_VALUE;
                long actualMax = max != null ? max : Long.MAX_VALUE;
                return current >= actualMin && current <= actualMax;
            }
            return EffectRecalculationService.compareLong(current, condition.configString("op"), condition.configLong("value"));
        });
        registerConditionHandler("hunger", (context, condition) -> {
            if (!(context.evaluationContext().entity() instanceof Player player)) {
                return false;
            }
            String mode = condition.configString("mode");
            long current = switch (mode == null ? "value" : mode) {
                case "saturation" -> Math.round(player.getFoodData().getSaturationLevel());
                case "missing" -> 20L - player.getFoodData().getFoodLevel();
                default -> player.getFoodData().getFoodLevel();
            };
            Long min = condition.configLong("min");
            Long max = condition.configLong("max");
            if (min != null || max != null) {
                long actualMin = min != null ? min : Long.MIN_VALUE;
                long actualMax = max != null ? max : Long.MAX_VALUE;
                return current >= actualMin && current <= actualMax;
            }
            return EffectRecalculationService.compareLong(current, condition.configString("op"), condition.configLong("value"));
        });
        registerConditionHandler("air", (context, condition) -> {
            if (!(context.evaluationContext().entity() instanceof net.minecraft.world.entity.LivingEntity living)) {
                return false;
            }
            if ("underwater".equals(condition.configString("mode"))) {
                return living.isUnderWater();
            }
            return EffectRecalculationService.compareLong(living.getAirSupply(), condition.configString("op"), condition.configLong("value"));
        });
        registerConditionHandler("xp", (context, condition) -> {
            if (!(context.evaluationContext().entity() instanceof Player player)) {
                return false;
            }
            long current = "total".equals(condition.configString("mode")) ? player.totalExperience : player.experienceLevel;
            Long min = condition.configLong("min");
            Long max = condition.configLong("max");
            if (min != null || max != null) {
                long actualMin = min != null ? min : Long.MIN_VALUE;
                long actualMax = max != null ? max : Long.MAX_VALUE;
                return current >= actualMin && current <= actualMax;
            }
            return EffectRecalculationService.compareLong(current, condition.configString("op"), condition.configLong("value"));
        });
        registerConditionHandler("status_effect", (context, condition) -> {
            if (!(context.evaluationContext().entity() instanceof net.minecraft.world.entity.LivingEntity living)) {
                return false;
            }
            ResourceLocation effectId = ResourceLocation.tryParse(condition.configString("effect"));
            if (effectId == null) {
                return false;
            }
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(effectId);
            if (effect == null) {
                return false;
            }
            MobEffectInstance instance = living.getEffect(effect);
            if (instance == null) {
                return false;
            }
            Long amplifier = condition.configLong("amplifier");
            if (amplifier != null && instance.getAmplifier() < amplifier) {
                return false;
            }
            Long minDuration = condition.configLong("min_duration");
            if (minDuration != null && instance.getDuration() < minDuration) {
                return false;
            }
            Long maxDuration = condition.configLong("max_duration");
            return maxDuration == null || instance.getDuration() <= maxDuration;
        });
        registerConditionHandler("attribute", (context, condition) -> {
            if (!(context.evaluationContext().entity() instanceof net.minecraft.world.entity.LivingEntity living)) {
                return false;
            }
            ResourceLocation attributeId = ResourceLocation.tryParse(condition.configString("attribute"));
            if (attributeId == null) {
                return false;
            }
            net.minecraft.world.entity.ai.attributes.Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(attributeId);
            if (attribute == null) {
                return false;
            }
            double current = living.getAttributeValue(attribute);
            Long min = condition.configLong("min");
            Long max = condition.configLong("max");
            if (min != null || max != null) {
                double actualMin = min != null ? min.doubleValue() : Double.NEGATIVE_INFINITY;
                double actualMax = max != null ? max.doubleValue() : Double.POSITIVE_INFINITY;
                return current >= actualMin && current <= actualMax;
            }
            return EffectRecalculationService.compareLong(Math.round(current), condition.configString("op"), condition.configLong("value"));
        });
        registerConditionHandler("movement_state", (context, condition) -> {
            if (!(context.evaluationContext().entity() instanceof net.minecraft.world.entity.LivingEntity living)) {
                return false;
            }
            return switch (String.valueOf(condition.configString("state"))) {
                case "sprinting" -> living.isSprinting();
                case "sneaking" -> living.isCrouching();
                case "swimming" -> living.isSwimming();
                case "fall_flying" -> living.isFallFlying();
                case "on_ground" -> living.onGround();
                default -> false;
            };
        });
        registerConditionHandler("environment_state", (context, condition) -> {
            if (!(context.evaluationContext().entity() instanceof net.minecraft.world.entity.LivingEntity living)) {
                return false;
            }
            return switch (String.valueOf(condition.configString("state"))) {
                case "in_water" -> living.isInWater();
                case "underwater" -> living.isUnderWater();
                case "on_fire" -> living.isOnFire();
                case "riding" -> living.isPassenger();
                case "wet" -> living.isInWaterRainOrBubble() || living.isInLava();
                default -> false;
            };
        });
        registerConditionHandler("equipment", (context, condition) -> {
            if (!(context.evaluationContext().entity() instanceof net.minecraft.world.entity.LivingEntity living)) {
                return false;
            }
            EquipmentSlot slot = switch (String.valueOf(condition.configString("slot"))) {
                case "mainhand" -> EquipmentSlot.MAINHAND;
                case "offhand" -> EquipmentSlot.OFFHAND;
                case "head" -> EquipmentSlot.HEAD;
                case "chest" -> EquipmentSlot.CHEST;
                case "legs" -> EquipmentSlot.LEGS;
                case "feet" -> EquipmentSlot.FEET;
                default -> null;
            };
            if (slot == null) {
                return false;
            }
            ItemStack stack = living.getItemBySlot(slot);
            Boolean empty = condition.configBoolean("empty");
            if (empty != null) {
                return empty == stack.isEmpty();
            }
            String itemId = condition.configString("item");
            if (itemId != null) {
                ResourceLocation expected = ResourceLocation.tryParse(itemId);
                ResourceLocation actual = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (expected == null || actual == null || !expected.equals(actual)) {
                    return false;
                }
            }
            String itemTag = condition.configString("item_tag");
            if (itemTag != null) {
                ResourceLocation tagId = ResourceLocation.tryParse(itemTag);
                if (tagId == null || !stack.is(TagKey.create(net.minecraft.core.registries.Registries.ITEM, tagId))) {
                    return false;
                }
            }
            return !stack.isEmpty();
        });
        registerConditionHandler("enchantment", (context, condition) -> {
            if (!(context.evaluationContext().entity() instanceof net.minecraft.world.entity.LivingEntity living)) {
                return false;
            }
            EquipmentSlot slot = switch (String.valueOf(condition.configString("slot"))) {
                case "offhand" -> EquipmentSlot.OFFHAND;
                case "head" -> EquipmentSlot.HEAD;
                case "chest" -> EquipmentSlot.CHEST;
                case "legs" -> EquipmentSlot.LEGS;
                case "feet" -> EquipmentSlot.FEET;
                default -> EquipmentSlot.MAINHAND;
            };
            ItemStack stack = living.getItemBySlot(slot);
            ResourceLocation enchantmentId = ResourceLocation.tryParse(condition.configString("enchantment"));
            if (stack.isEmpty() || enchantmentId == null) {
                return false;
            }
            net.minecraft.world.item.enchantment.Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(enchantmentId);
            if (enchantment == null) {
                return false;
            }
            int level = stack.getEnchantmentLevel(enchantment);
            if (level <= 0) {
                return false;
            }
            return EffectRecalculationService.compareLong(level, condition.configString("op"), condition.configLong("value"));
        });
        registerConditionHandler("nearby_entity", (context, condition) -> {
            net.minecraft.world.entity.Entity entity = context.evaluationContext().entity();
            double radius = condition.configDouble("radius") != null ? condition.configDouble("radius") : 8.0D;
            AABB area = entity.getBoundingBox().inflate(radius);
            List<net.minecraft.world.entity.LivingEntity> entities = entity.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, area, other -> other != entity);
            String targetId = condition.configString("entity");
            String targetTag = condition.configString("entity_tag");
            long count = 0L;
            for (net.minecraft.world.entity.LivingEntity other : entities) {
                if (targetId != null) {
                    ResourceLocation actual = ForgeRegistries.ENTITY_TYPES.getKey(other.getType());
                    if (actual == null || !targetId.equals(actual.toString())) {
                        continue;
                    }
                }
                if (targetTag != null) {
                    ResourceLocation tagId = ResourceLocation.tryParse(targetTag);
                    if (tagId == null || !other.getType().is(TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, tagId))) {
                        continue;
                    }
                }
                count++;
            }
            Long min = condition.configLong("min");
            Long max = condition.configLong("max");
            if (min != null || max != null) {
                long actualMin = min != null ? min : Long.MIN_VALUE;
                long actualMax = max != null ? max : Long.MAX_VALUE;
                return count >= actualMin && count <= actualMax;
            }
            return EffectRecalculationService.compareLong(count, condition.configString("op"), condition.configLong("value"));
        });
        registerConditionHandler("moon_phase", (context, condition) -> {
            long phase = Math.floorMod(context.evaluationContext().entity().level().getDayTime() / EffectRecalculationService.dayTicks(), 8L);
            String mode = condition.configString("mode");
            if (mode != null && !mode.isBlank()) {
                return switch (mode) {
                    case "new_moon" -> phase == 0L;
                    case "waxing_crescent" -> phase == 1L;
                    case "first_quarter" -> phase == 2L;
                    case "waxing_gibbous" -> phase == 3L;
                    case "full_moon" -> phase == 4L;
                    case "waning_gibbous" -> phase == 5L;
                    case "last_quarter" -> phase == 6L;
                    case "waning_crescent" -> phase == 7L;
                    default -> false;
                };
            }
            return EffectRecalculationService.compareLong(phase, condition.configString("op"), condition.configLong("value"));
        });
        registerConditionHandler("biome_category", (context, condition) -> {
            String value = condition.configString("value");
            if (value == null) {
                return false;
            }
            ResourceLocation biomeId = context.evaluationContext().biome().unwrapKey().map(ResourceKey::location).orElse(null);
            if (biomeId == null) {
                return false;
            }
            String normalizedValue = value.toLowerCase(java.util.Locale.ROOT);
            String path = biomeId.getPath().toLowerCase(java.util.Locale.ROOT);
            return path.equals(normalizedValue) || path.contains(normalizedValue);
        });
        registerConditionHandler("dimension_type", (context, condition) -> {
            String value = condition.configString("value");
            if (value == null) {
                return false;
            }
            String dimensionTypeId = context.evaluationContext().entity().level().dimensionType().effectsLocation().toString();
            return value.equals(dimensionTypeId);
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
        registerEventFilter("damage_type", (eventRule, event, key, value) -> {
            String damageType = event.extraString("damage_type");
            return damageType != null && damageType.equals(value.getAsString());
        });
        registerEventFilter("damage_type_tag", (eventRule, event, key, value) -> {
            String damageTypeTag = event.extraString("damage_type_tag");
            return damageTypeTag != null && damageTypeTag.equals(value.getAsString());
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
            long durationTicks = action.durationTicks() != null && action.durationTicks() > 0 ? action.durationTicks() : 60L;
            long refreshTicks = Math.max(1L, action.configLong("refresh_ticks") != null ? action.configLong("refresh_ticks") : 10L);
            RuntimeEffectService.registerTaunt(player, radius, durationTicks, refreshTicks, action.target());
        }
    }

    private static final class DamageSelfExecutor implements PointExecutor {
        @Override
        public String type() {
            return "damage_self";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            float amount = action.amount() != null ? action.amount().floatValue() : 1.0F;
            context.player().hurt(context.player().level().damageSources().generic(), amount);
        }
    }

    private static final class DamageTargetExecutor implements PointExecutor {
        @Override
        public String type() {
            return "damage_target";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            net.minecraft.world.entity.LivingEntity target = findNearestLivingTarget(context.player(), action.configDouble("radius") != null ? action.configDouble("radius") : 6.0D);
            if (target == null) {
                return;
            }
            float amount = action.amount() != null ? action.amount().floatValue() : 1.0F;
            target.hurt(target.level().damageSources().generic(), amount);
        }
    }

    private static final class TeleportExecutor implements PointExecutor {
        @Override
        public String type() {
            return "teleport";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            Player player = context.player();
            double dx = action.configDouble("dx") != null ? action.configDouble("dx") : 0.0D;
            double dy = action.configDouble("dy") != null ? action.configDouble("dy") : 0.0D;
            double dz = action.configDouble("dz") != null ? action.configDouble("dz") : 0.0D;
            double x = action.configDouble("x") != null ? action.configDouble("x") : player.getX() + dx;
            double y = action.configDouble("y") != null ? action.configDouble("y") : player.getY() + dy;
            double z = action.configDouble("z") != null ? action.configDouble("z") : player.getZ() + dz;
            player.teleportTo(x, y, z);
        }
    }

    private static final class KnockbackExecutor implements PointExecutor {
        @Override
        public String type() {
            return "knockback";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            Player player = context.player();
            float strength = action.amount() != null ? action.amount().floatValue() : 1.0F;
            float vertical = action.configDouble("vertical") != null ? action.configDouble("vertical").floatValue() : 0.1F;
            net.minecraft.world.phys.Vec3 look = player.getLookAngle().normalize();
            player.push(-look.x * strength, vertical, -look.z * strength);
        }
    }

    private static final class LaunchExecutor implements PointExecutor {
        @Override
        public String type() {
            return "launch";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            Player player = context.player();
            double y = action.configDouble("y") != null ? action.configDouble("y") : 0.8D;
            player.push(0.0D, y, 0.0D);
        }
    }

    private static final class GiveXpExecutor implements PointExecutor {
        @Override
        public String type() {
            return "give_xp";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            int xp = action.amount() != null ? action.amount().intValue() : 1;
            context.player().giveExperiencePoints(Math.max(0, xp));
        }
    }

    private static final class ClearNegativeEffectsExecutor implements PointExecutor {
        @Override
        public String type() {
            return "clear_negative_effects";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            for (MobEffectInstance instance : context.player().getActiveEffectsMap().values()) {
                if (!instance.getEffect().isBeneficial()) {
                    context.player().removeEffect(instance.getEffect());
                }
            }
        }
    }

    private static final class RemoveEffectExecutor implements PointExecutor {
        @Override
        public String type() {
            return "remove_effect";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            ResourceLocation effectId = ResourceLocation.tryParse(action.effectId() != null ? action.effectId() : action.configString("effect"));
            if (effectId == null) {
                return;
            }
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(effectId);
            if (effect != null) {
                context.player().removeEffect(effect);
            }
        }
    }

    private static final class SetFireExecutor implements PointExecutor {
        @Override
        public String type() {
            return "set_fire";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            int seconds = action.amount() != null ? action.amount().intValue() : 4;
            context.player().setSecondsOnFire(Math.max(1, seconds));
        }
    }

    private static final class ExtinguishExecutor implements PointExecutor {
        @Override
        public String type() {
            return "extinguish";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            context.player().clearFire();
        }
    }

    private static final class ConsumeHungerExecutor implements PointExecutor {
        @Override
        public String type() {
            return "consume_hunger";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            Player player = context.player();
            int amount = action.amount() != null ? Math.max(1, action.amount().intValue()) : 1;
            int newFood = Math.max(0, player.getFoodData().getFoodLevel() - amount);
            player.getFoodData().setFoodLevel(newFood);
        }
    }

    private static final class RestoreAirExecutor implements PointExecutor {
        @Override
        public String type() {
            return "restore_air";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            net.minecraft.world.entity.LivingEntity living = context.player();
            int amount = action.amount() != null ? Math.max(1, action.amount().intValue()) : 20;
            living.setAirSupply(Math.min(living.getMaxAirSupply(), living.getAirSupply() + amount));
        }
    }

    private static final class ConsumeItemExecutor implements PointExecutor {
        @Override
        public String type() {
            return "consume_item";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            Player player = context.player();
            ItemStack stack = player.getMainHandItem();
            int amount = action.amount() != null ? Math.max(1, action.amount().intValue()) : 1;
            stack.shrink(amount);
        }
    }

    private static final class RepairItemExecutor implements PointExecutor {
        @Override
        public String type() {
            return "repair_item";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            ItemStack stack = context.player().getMainHandItem();
            if (!stack.isDamageableItem() || stack.isEmpty()) {
                return;
            }
            int amount = action.amount() != null ? Math.max(1, action.amount().intValue()) : 1;
            stack.setDamageValue(Math.max(0, stack.getDamageValue() - amount));
        }
    }

    private static final class SetCooldownExecutor implements PointExecutor {
        @Override
        public String type() {
            return "set_cooldown";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            ItemStack stack = context.player().getMainHandItem();
            if (stack.isEmpty()) {
                return;
            }
            int ticks = action.durationTicks() != null ? Math.max(1, action.durationTicks()) : Math.max(1, action.amount() != null ? action.amount().intValue() : 20);
            context.player().getCooldowns().addCooldown(stack.getItem(), ticks);
        }
    }

    private static final class DropItemsExecutor implements PointExecutor {
        @Override
        public String type() {
            return "drop_items";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L || action.items().isEmpty()) {
                return;
            }
            Player player = context.player();
            for (EffectDefinition.ItemEntry entry : action.items()) {
                ResourceLocation itemId = ResourceLocation.tryParse(entry.itemId());
                if (itemId == null) {
                    continue;
                }
                Item item = ForgeRegistries.ITEMS.getValue(itemId);
                if (item == null) {
                    continue;
                }
                player.spawnAtLocation(new ItemStack(item, Math.max(1, entry.count())));
            }
        }
    }

    private static final class ForceTargetExecutor implements PointExecutor {
        @Override
        public String type() {
            return "force_target";
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
            List<Mob> mobs = player.level().getEntitiesOfClass(Mob.class, area, mob -> mob instanceof Enemy && mob.isAlive() && mob.canAttack(player));
            for (Mob mob : mobs) {
                mob.setTarget(player);
                mob.setLastHurtByMob(player);
            }
        }
    }

    private static final class PlaySoundExecutor implements PointExecutor {
        @Override
        public String type() {
            return "play_sound";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            ResourceLocation soundId = ResourceLocation.tryParse(action.configString("sound"));
            if (soundId == null) {
                return;
            }
            var sound = BuiltInRegistries.SOUND_EVENT.get(soundId);
            if (sound == null) {
                return;
            }
            float volume = action.configDouble("volume") != null ? action.configDouble("volume").floatValue() : 1.0F;
            float pitch = action.configDouble("pitch") != null ? action.configDouble("pitch").floatValue() : 1.0F;
            context.player().level().playSound(null, context.player().blockPosition(), sound, net.minecraft.sounds.SoundSource.PLAYERS, volume, pitch);
        }
    }

    private static final class SpawnParticleExecutor implements PointExecutor {
        @Override
        public String type() {
            return "spawn_particle";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            if (!(context.player().level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
                return;
            }
            ResourceLocation particleId = ResourceLocation.tryParse(action.configString("particle"));
            if (particleId == null) {
                return;
            }
            var particleType = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(particleId);
            if (!(particleType instanceof net.minecraft.core.particles.SimpleParticleType simpleParticle)) {
                return;
            }
            int count = action.configLong("count") != null ? Math.max(1, action.configLong("count").intValue()) : 1;
            double x = action.configDouble("x") != null ? action.configDouble("x") : context.player().getX();
            double y = action.configDouble("y") != null ? action.configDouble("y") : context.player().getY();
            double z = action.configDouble("z") != null ? action.configDouble("z") : context.player().getZ();
            serverLevel.sendParticles(simpleParticle, x, y, z, count, 0.2D, 0.2D, 0.2D, 0.0D);
        }
    }

    private static final class SummonEntityExecutor implements PointExecutor {
        @Override
        public String type() {
            return "summon_entity";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            ResourceLocation entityId = ResourceLocation.tryParse(action.configString("entity"));
            if (entityId == null) {
                return;
            }
            var entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
            if (entityType == null) {
                return;
            }
            var entity = entityType.create(context.player().level());
            if (entity == null) {
                return;
            }
            entity.moveTo(context.player().getX(), context.player().getY(), context.player().getZ(), context.player().getYRot(), context.player().getXRot());
            context.player().level().addFreshEntity(entity);
        }
    }

    private static final class PlaceBlockExecutor implements PointExecutor {
        @Override
        public String type() {
            return "place_block";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            ResourceLocation blockId = ResourceLocation.tryParse(action.configString("block"));
            if (blockId == null) {
                return;
            }
            var block = ForgeRegistries.BLOCKS.getValue(blockId);
            if (block == null) {
                return;
            }
            var level = context.player().level();
            net.minecraft.core.BlockPos pos = context.player().blockPosition().above();
            if (!level.getBlockState(pos).isAir()) {
                return;
            }
            level.setBlockAndUpdate(pos, block.defaultBlockState());
        }
    }

    private static final class ConvertBlockExecutor implements PointExecutor {
        @Override
        public String type() {
            return "convert_block";
        }

        @Override
        public void execute(PointExecutionContext context, EffectDefinition.BonusAction action) {
            PointUsage usage = context.resolveUsage(action);
            if (usage.usedPoints() <= 0L) {
                return;
            }
            ResourceLocation fromId = ResourceLocation.tryParse(action.configString("from"));
            ResourceLocation toId = ResourceLocation.tryParse(action.configString("to"));
            if (toId == null) {
                return;
            }
            var toBlock = ForgeRegistries.BLOCKS.getValue(toId);
            if (toBlock == null) {
                return;
            }
            var level = context.player().level();
            net.minecraft.core.BlockPos pos = context.player().blockPosition().below();
            if (fromId != null) {
                var fromBlock = ForgeRegistries.BLOCKS.getValue(fromId);
                if (fromBlock == null || level.getBlockState(pos).getBlock() != fromBlock) {
                    return;
                }
            }
            level.setBlockAndUpdate(pos, toBlock.defaultBlockState());
        }
    }

    private static net.minecraft.world.entity.LivingEntity findNearestLivingTarget(Player player, double radius) {
        AABB area = player.getBoundingBox().inflate(radius);
        List<net.minecraft.world.entity.LivingEntity> candidates = player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, area,
                entity -> entity != player && entity.isAlive() && !(entity instanceof Player));
        net.minecraft.world.entity.LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (net.minecraft.world.entity.LivingEntity candidate : candidates) {
            double distance = candidate.distanceToSqr(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }
}
