package cn.kuzuanpa.organeffectprocessor.common.effect;

import cn.kuzuanpa.organapi.api.query.OrganPosition;
import cn.kuzuanpa.organapi.api.query.OrganQueryService;
import cn.kuzuanpa.organapi.common.data.OrganRegistryAccess;
import cn.kuzuanpa.organeffectprocessor.api.EffectDefinition;
import cn.kuzuanpa.organeffectprocessor.api.extension.OepExtensionApi;
import cn.kuzuanpa.organeffectprocessor.api.extension.OepRuntimeEvent;
import cn.kuzuanpa.organeffectprocessor.common.capability.EffectCapabilities;
import cn.kuzuanpa.organeffectprocessor.common.capability.IEffectHolder;
import cn.kuzuanpa.organeffectprocessor.common.data.OrganEffectData;
import cn.kuzuanpa.organeffectprocessor.common.debug.OepDebug;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

public final class RuntimeEffectService {
    private static final Map<UUID, Double> MOVE_REMAINDERS = new HashMap<>();

    private RuntimeEffectService() {
    }

    public static void handleMove(Player player, double distanceMoved) {
        if (distanceMoved <= 0.0D) {
            return;
        }
        double totalDistance = MOVE_REMAINDERS.getOrDefault(player.getUUID(), 0.0D) + distanceMoved;
        MoveResult result = handleMoveEvent(player, totalDistance);
        MOVE_REMAINDERS.put(player.getUUID(), Math.max(0.0D, result.remainder()));
    }

    public static void handleEat(LivingEntity entity, ItemStack stack) {
        fireEvent(entity, OepRuntimeEvent.builder("eat", entity).itemStack(stack).build());
    }

    public static void handleMine(Player player, BlockState state) {
        fireEvent(player, OepRuntimeEvent.builder("mine", player).blockState(state).build());
    }

    public static void handleUseItem(Player player, ItemStack stack) {
        fireEvent(player, OepRuntimeEvent.builder("use_item", player).itemStack(stack).build());
    }

    public static void handleAttacked(LivingEntity victim, LivingEntity attacker, Entity directEntity, float damageAmount) {
        fireEvent(victim, OepRuntimeEvent.builder("attacked", victim)
                .target(attacker)
                .directEntity(directEntity)
                .amount(damageAmount)
                .build());
    }

    public static void handleHealthLoss(LivingEntity entity, Entity sourceEntity, Entity directEntity, float damageAmount) {
        LivingEntity attacker = sourceEntity instanceof LivingEntity living ? living : null;
        fireEvent(entity, OepRuntimeEvent.builder("health_loss", entity)
                .target(attacker)
                .directEntity(directEntity)
                .amount(damageAmount)
                .build());
    }

    public static void handleKill(LivingEntity attacker, LivingEntity target, Entity directEntity) {
        fireEvent(attacker, OepRuntimeEvent.builder("kill", attacker)
                .target(target)
                .directEntity(directEntity)
                .build());
    }

    public static void handleBiomeChange(Player player) {
        fireEvent(player, OepRuntimeEvent.builder("biome_change", player).build());
    }

    public static void handleDimensionChange(Player player) {
        fireEvent(player, OepRuntimeEvent.builder("dimension_change", player).build());
    }

    public static void handleAttack(LivingEntity attacker, LivingEntity target, Entity directEntity, float baseDamage) {
        fireEvent(attacker, OepRuntimeEvent.builder("attack", attacker)
                .target(target)
                .directEntity(directEntity)
                .amount(baseDamage)
                .build());
    }

    public static void fireEvent(LivingEntity entity, OepRuntimeEvent event) {
        handleEvent(entity, event);
    }

    private static MoveResult handleMoveEvent(Player player, double totalDistance) {
        IEffectHolder holder = player.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder == null) {
            return new MoveResult(0.0D);
        }
        double maxConsumedDistance = 0.0D;
        OepRuntimeEvent event = OepRuntimeEvent.builder("move", player)
                .distanceMoved(totalDistance)
                .build();
        for (EffectInstance instance : collectMatchingEffects(player, event.type())) {
            if (!EffectRecalculationService.evaluateConditions(player, instance.position(), instance.effect().conditions())) {
                continue;
            }
            for (EffectDefinition.EventRule eventRule : instance.effect().events()) {
                if (!event.type().equals(eventRule.type()) || !matchesEventFilter(eventRule, event)) {
                    continue;
                }
                long distance = eventRule.distance() != null ? eventRule.distance() : 1L;
                if (distance <= 0L) {
                    distance = 1L;
                }
                long steps = (long) Math.floor(totalDistance / distance);
                if (steps <= 0L) {
                    continue;
                }
                applyPointMutations(player, holder, instance, eventRule.addPoints(), steps);
                applyConsumes(player, holder, instance, eventRule.consumePoints());
                RuntimePointExecutor.execute(player);
                maxConsumedDistance = Math.max(maxConsumedDistance, steps * distance);
            }
        }
        return new MoveResult(totalDistance - maxConsumedDistance);
    }

    private static void handleEvent(Entity entity, OepRuntimeEvent event) {
        IEffectHolder holder = entity.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder == null) {
            return;
        }
        for (EffectInstance instance : collectMatchingEffects(entity, event.type())) {
            boolean conditionsMatch = EffectRecalculationService.evaluateConditions(entity, instance.position(), instance.effect().conditions());
            if (entity instanceof Player player) {
                OepDebug.trace(player, "event %s organ=%s slot=%s#%d conditions=%s", event.type(), instance.organId(), instance.position().bodyPartId(), instance.position().slotIndex(), conditionsMatch);
            }
            if (!conditionsMatch) {
                continue;
            }
            for (EffectDefinition.EventRule eventRule : instance.effect().events()) {
                boolean filterMatch = event.type().equals(eventRule.type()) && matchesEventFilter(eventRule, event);
                if (entity instanceof Player player) {
                    OepDebug.trace(player, "event rule %s matched=%s", eventRule.type(), filterMatch);
                }
                if (!filterMatch) {
                    continue;
                }
                applyPointMutations(entity, holder, instance, eventRule.addPoints(), 1L);
                applyConsumes(entity, holder, instance, eventRule.consumePoints());
                if (entity instanceof Player player) {
                    RuntimePointExecutor.execute(player);
                }
            }
        }
    }

    private static void applyPointMutations(Entity entity, IEffectHolder holder, EffectInstance instance, List<EffectDefinition.PointMutation> mutations, long multiplier) {
        for (EffectDefinition.PointMutation mutation : mutations) {
            long amount = mutation.amount();
            if (amount == 0L || multiplier <= 0L) {
                continue;
            }
            long successfulApplications = countSuccessfulApplications(entity, mutation.chance(), multiplier);
            if (successfulApplications <= 0L) {
                if (entity instanceof Player player) {
                    OepDebug.trace(player, "mutation skipped %s:%s attempts=%d", mutation.type(), mutation.id(), multiplier);
                }
                continue;
            }
            long totalAmount = amount * successfulApplications;
            String pointKey = mutation.type() + ":" + mutation.id();
            long expireAtTick = mutation.durationTicks() != null && mutation.durationTicks() > 0
                    ? entity.level().getGameTime() + mutation.durationTicks()
                    : 0L;
            if (expireAtTick > 0L) {
                long before = holder.getRuntimePoints().getOrDefault(pointKey, 0L);
                long after = holder.addRuntimePoint(pointKey, totalAmount, expireAtTick);
                if (entity instanceof Player player) {
                    OepDebug.trace(player, "+runtime %s %d -> %d expires@%d", pointKey, before, after, expireAtTick);
                }
            } else {
                String source = resolveSource(instance, mutation.source());
                long before = holder.getPointsForSource(source).getOrDefault(pointKey, 0L);
                long after = holder.addSourcePoint(source, pointKey, totalAmount);
                if (entity instanceof Player player) {
                    OepDebug.trace(player, "+source %s %s %d -> %d", source, pointKey, before, after);
                }
            }
        }
    }

    private static void applyConsumes(Entity entity, IEffectHolder holder, EffectInstance instance, List<EffectDefinition.PointMutation> mutations) {
        for (EffectDefinition.PointMutation mutation : mutations) {
            long amount = mutation.amount();
            if (amount <= 0L || !shouldApplyMutation(entity, mutation.chance())) {
                continue;
            }
            String pointKey = mutation.type() + ":" + mutation.id();
            if (mutation.durationTicks() != null && mutation.durationTicks() > 0) {
                long before = holder.getRuntimePoints().getOrDefault(pointKey, 0L);
                long used = holder.consumeRuntimePoint(pointKey, amount);
                long after = holder.getRuntimePoints().getOrDefault(pointKey, 0L);
                if (entity instanceof Player player) {
                    OepDebug.trace(player, "consume runtime %s used=%d %d -> %d", pointKey, used, before, after);
                }
            } else {
                long before = holder.getPooledSourcePoints(pointKey, mutation.source());
                long used = holder.consumePooledSourcePoints(pointKey, mutation.source(), amount);
                long after = holder.getPooledSourcePoints(pointKey, mutation.source());
                if (entity instanceof Player player) {
                    OepDebug.trace(player, "consume pooled source=%s %s used=%d %d -> %d", mutation.source(), pointKey, used, before, after);
                }
            }
        }
    }

    private static boolean matchesEventFilter(EffectDefinition.EventRule eventRule, OepRuntimeEvent event) {
        if (eventRule.foodOnly() && (event.itemStack().isEmpty() || !event.itemStack().isEdible())) {
            return false;
        }
        if (eventRule.item() != null) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(event.itemStack().getItem());
            if (!Objects.equals(eventRule.item(), itemId != null ? itemId.toString() : null)) {
                return false;
            }
        }
        if (eventRule.itemTag() != null && !event.itemStack().is(tag(ItemTags::create, eventRule.itemTag()))) {
            return false;
        }
        if (eventRule.block() != null) {
            ResourceLocation blockId = event.blockState() == null ? null : ForgeRegistries.BLOCKS.getKey(event.blockState().getBlock());
            if (!Objects.equals(eventRule.block(), blockId != null ? blockId.toString() : null)) {
                return false;
            }
        }
        if (eventRule.blockTag() != null && (event.blockState() == null || !event.blockState().is(tag(BlockTags::create, eventRule.blockTag())))) {
            return false;
        }
        return OepExtensionApi.matchesExtraEventFilters(eventRule, event);
    }

    private static long countSuccessfulApplications(Entity entity, EffectDefinition.ChanceConfig chance, long attempts) {
        if (attempts <= 0L) {
            return 0L;
        }
        if (chance == null) {
            return attempts;
        }
        double effectiveChance = computeEffectiveChance(entity, chance);
        if (effectiveChance <= 0.0D) {
            return 0L;
        }
        if (effectiveChance >= 1.0D) {
            return attempts;
        }
        RandomSource random = entity.level().random;
        long successes = 0L;
        for (long index = 0L; index < attempts; index++) {
            if (random.nextDouble() < effectiveChance) {
                successes++;
            }
        }
        return successes;
    }

    private static boolean shouldApplyMutation(Entity entity, EffectDefinition.ChanceConfig chance) {
        return countSuccessfulApplications(entity, chance, 1L) > 0L;
    }

    private static double computeEffectiveChance(Entity entity, EffectDefinition.ChanceConfig chance) {
        if (chance == null) {
            return 1.0D;
        }
        double base = chance.base() != null ? chance.base() : 0.0D;
        double luckyStep = chance.luckyStep() != null ? chance.luckyStep() : 0.0D;
        double luck = entity instanceof LivingEntity livingEntity ? livingEntity.getAttributeValue(Attributes.LUCK) : 0.0D;
        double effectiveChance = base + luck * luckyStep;
        if (chance.max() != null) {
            effectiveChance = Math.min(effectiveChance, chance.max());
        }
        return Math.max(0.0D, Math.min(1.0D, effectiveChance));
    }

    private static <T> TagKey<T> tag(TagFactory<T> factory, String rawTag) {
        return factory.create(ResourceLocation.tryParse(rawTag));
    }

    private static List<EffectInstance> collectMatchingEffects(Entity entity, String eventType) {
        Map<String, EffectInstance> effects = new LinkedHashMap<>();
        for (OrganPosition position : OrganQueryService.getInstalledOrganPositions(entity)) {
            ResourceLocation organId = OrganRegistryAccess.getOrgan(position.organ()).map(definition -> definition.id()).orElse(null);
            if (organId == null) {
                continue;
            }
            int effectIndex = 0;
            for (EffectDefinition effect : OrganEffectData.INSTANCE.getEffectsForOrgan(organId)) {
                boolean hasEvent = effect.events().stream().anyMatch(eventRule -> eventType.equals(eventRule.type()));
                if (hasEvent) {
                    effects.put(effectKey(organId, position, effectIndex), new EffectInstance(organId, position, effect, effectIndex));
                }
                effectIndex++;
            }
        }
        return List.copyOf(effects.values());
    }

    private static String resolveSource(EffectInstance instance, String declaredSource) {
        if (declaredSource == null || declaredSource.isBlank() || "self".equals(declaredSource)) {
            return EffectRecalculationService.ORGAN_INSTANCE_SOURCE_PREFIX
                    + instance.organId() + "@" + instance.position().bodyPartId() + "#" + instance.position().slotIndex() + "/event/" + instance.effectIndex();
        }
        return declaredSource;
    }

    private static String effectKey(ResourceLocation organId, OrganPosition position, int effectIndex) {
        return organId + "@" + position.bodyPartId() + "#" + position.slotIndex() + "/effect/" + effectIndex;
    }

    private record EffectInstance(ResourceLocation organId, OrganPosition position, EffectDefinition effect, int effectIndex) {
    }

    private record MoveResult(double remainder) {
    }

    @FunctionalInterface
    private interface TagFactory<T> {
        TagKey<T> create(ResourceLocation id);
    }
}
