package cn.kuzuanpa.organeffectprocessor.common.effect;

import cn.kuzuanpa.organapi.api.query.OrganPosition;
import cn.kuzuanpa.organapi.api.query.OrganQueryService;
import cn.kuzuanpa.organapi.common.data.OrganRegistryAccess;
import cn.kuzuanpa.organeffectprocessor.api.EffectDefinition;
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
        handleEvent(entity, new RuntimeEventContext("eat", entity, null, null, stack, null, 0.0D, 0.0D));
    }

    public static void handleMine(Player player, BlockState state) {
        handleEvent(player, new RuntimeEventContext("mine", player, null, null, ItemStack.EMPTY, state, 0.0D, 0.0D));
    }

    public static void handleUseItem(Player player, ItemStack stack) {
        handleEvent(player, new RuntimeEventContext("use_item", player, null, null, stack, null, 0.0D, 0.0D));
    }

    public static float handleAttack(LivingEntity attacker, LivingEntity target, Entity directEntity, float baseDamage) {
        RuntimeEventContext eventContext = new RuntimeEventContext("attack", attacker, target, directEntity, ItemStack.EMPTY, null, 0.0D, baseDamage);
        return (float) (baseDamage + handleEvent(attacker, eventContext));
    }

    private static MoveResult handleMoveEvent(Player player, double totalDistance) {
        IEffectHolder holder = player.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder == null) {
            return new MoveResult(0.0D);
        }
        double maxConsumedDistance = 0.0D;
        RuntimeEventContext eventContext = new RuntimeEventContext("move", player, null, null, ItemStack.EMPTY, null, totalDistance, 0.0D);
        for (EffectInstance instance : collectMatchingEffects(player, eventContext.type())) {
            if (!EffectRecalculationService.evaluateConditions(player, instance.position(), instance.effect().conditions())) {
                continue;
            }
            for (EffectDefinition.EventRule eventRule : instance.effect().events()) {
                if (!eventContext.type().equals(eventRule.type()) || !matchesEventFilter(eventRule, eventContext)) {
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
                applyImmediateExecutions(eventContext, holder, instance, eventRule.actions(), steps);
                applyConsumes(player, holder, instance, eventRule.consumePoints());
                RuntimePointExecutor.execute(player);
                maxConsumedDistance = Math.max(maxConsumedDistance, steps * distance);
            }
        }
        return new MoveResult(totalDistance - maxConsumedDistance);
    }

    private static double handleEvent(Entity entity, RuntimeEventContext eventContext) {
        IEffectHolder holder = entity.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder == null) {
            return 0.0D;
        }
        double bonus = 0.0D;
        for (EffectInstance instance : collectMatchingEffects(entity, eventContext.type())) {
            boolean conditionsMatch = EffectRecalculationService.evaluateConditions(entity, instance.position(), instance.effect().conditions());
            if (entity instanceof Player player) {
                OepDebug.trace(player, "event %s organ=%s slot=%s#%d conditions=%s", eventContext.type(), instance.organId(), instance.position().bodyPartId(), instance.position().slotIndex(), conditionsMatch);
            }
            if (!conditionsMatch) {
                continue;
            }
            for (EffectDefinition.EventRule eventRule : instance.effect().events()) {
                boolean filterMatch = eventContext.type().equals(eventRule.type()) && matchesEventFilter(eventRule, eventContext);
                if (entity instanceof Player player) {
                    OepDebug.trace(player, "event rule %s matched=%s", eventRule.type(), filterMatch);
                }
                if (!filterMatch) {
                    continue;
                }
                applyPointMutations(entity, holder, instance, eventRule.addPoints(), 1L);
                bonus += applyImmediateExecutions(eventContext, holder, instance, eventRule.actions(), 1L);
                applyConsumes(entity, holder, instance, eventRule.consumePoints());
                if (entity instanceof Player player) {
                    RuntimePointExecutor.execute(player);
                }
            }
        }
        return bonus;
    }

    private static double applyImmediateExecutions(RuntimeEventContext eventContext, IEffectHolder holder, EffectInstance instance,
                                                   List<EffectDefinition.BonusAction> actions, long multiplier) {
        double damageBonus = 0.0D;
        for (EffectDefinition.BonusAction action : actions) {
            long successfulApplications = countSuccessfulApplications(eventContext.entity(), action.chance(), multiplier);
            if (successfulApplications <= 0L) {
                continue;
            }
            switch (action.type()) {
                case "bonus_damage_per_point", "modify_damage" -> damageBonus += applyModifyDamage(eventContext, holder, instance, action) * successfulApplications;
                case "mutate_points" -> {
                    for (long index = 0L; index < successfulApplications; index++) {
                        applyPointAction(eventContext.entity(), holder, instance, action);
                    }
                }
                default -> {
                }
            }
        }
        return damageBonus;
    }

    private static double applyModifyDamage(RuntimeEventContext eventContext, IEffectHolder holder, EffectInstance instance, EffectDefinition.BonusAction action) {
        if (!"attack".equals(eventContext.type()) || !matchesDamageKind(eventContext, action.damageKind())) {
            return 0.0D;
        }
        PointUsage usage = resolvePointUsage(holder, instance, action);
        return resolveFlatAmount(action) + usage.usedPoints() * resolveAmountPerPoint(action);
    }

    private static void applyPointAction(Entity entity, IEffectHolder holder, EffectInstance instance, EffectDefinition.BonusAction action) {
        if (action.pointType() == null || action.pointId() == null || action.pointOperation() == null) {
            return;
        }
        long amount = action.pointAmount() != null ? action.pointAmount() : 0L;
        if (amount <= 0L) {
            return;
        }
        long expireAtTick = action.durationTicks() != null && action.durationTicks() > 0 && entity != null
                ? entity.level().getGameTime() + action.durationTicks()
                : 0L;
        String pointKey = action.pointType() + ":" + action.pointId();
        switch (action.pointOperation()) {
            case "add" -> holder.addRuntimePoint(pointKey, amount, expireAtTick);
            case "consume" -> holder.consumeRuntimePoint(pointKey, amount);
            default -> {
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
                String source = resolveSource(instance, mutation.source());
                long before = holder.getPointsForSource(source).getOrDefault(pointKey, 0L);
                long used = holder.consumeSourcePoint(source, pointKey, amount);
                long after = holder.getPointsForSource(source).getOrDefault(pointKey, 0L);
                if (entity instanceof Player player) {
                    OepDebug.trace(player, "consume source %s %s used=%d %d -> %d", source, pointKey, used, before, after);
                }
            }
        }
    }

    private static PointUsage resolvePointUsage(IEffectHolder holder, EffectInstance instance, EffectDefinition.BonusAction action) {
        if (action.pointType() == null || action.pointId() == null) {
            return new PointUsage(0L);
        }
        String pointKey = action.pointType() + ":" + action.pointId();
        long runtimeAvailable = holder.getRuntimePoints().getOrDefault(pointKey, 0L);
        if (runtimeAvailable > 0L) {
            long capped = Math.min(runtimeAvailable, action.maxConsume());
            long used = action.consumePoints() ? holder.consumeRuntimePoint(pointKey, capped) : capped;
            return new PointUsage(Math.max(0L, used));
        }
        String source = resolveSource(instance, action.source());
        long available = holder.getPointsForSource(source).getOrDefault(pointKey, 0L);
        long capped = Math.min(available, action.maxConsume());
        long used = action.consumePoints() ? holder.consumeSourcePoint(source, pointKey, capped) : capped;
        return new PointUsage(Math.max(0L, used));
    }

    private static double resolveFlatAmount(EffectDefinition.BonusAction action) {
        return action.amount() != null ? action.amount() : 0.0D;
    }

    private static double resolveAmountPerPoint(EffectDefinition.BonusAction action) {
        return action.amountPerPoint() != null ? action.amountPerPoint() : 0.0D;
    }

    private static boolean matchesDamageKind(RuntimeEventContext eventContext, String damageKind) {
        String kind = damageKind == null ? "any" : damageKind;
        return switch (kind) {
            case "melee" -> !eventContext.isProjectileAttack();
            case "projectile" -> eventContext.isProjectileAttack();
            default -> true;
        };
    }

    private static boolean matchesEventFilter(EffectDefinition.EventRule eventRule, RuntimeEventContext eventContext) {
        if (eventRule.foodOnly() && (eventContext.itemStack().isEmpty() || !eventContext.itemStack().isEdible())) {
            return false;
        }
        if (eventRule.item() != null) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(eventContext.itemStack().getItem());
            if (!Objects.equals(eventRule.item(), itemId != null ? itemId.toString() : null)) {
                return false;
            }
        }
        if (eventRule.itemTag() != null && !eventContext.itemStack().is(tag(ItemTags::create, eventRule.itemTag()))) {
            return false;
        }
        if (eventRule.block() != null) {
            ResourceLocation blockId = eventContext.blockState() == null ? null : ForgeRegistries.BLOCKS.getKey(eventContext.blockState().getBlock());
            if (!Objects.equals(eventRule.block(), blockId != null ? blockId.toString() : null)) {
                return false;
            }
        }
        if (eventRule.blockTag() != null && (eventContext.blockState() == null || !eventContext.blockState().is(tag(BlockTags::create, eventRule.blockTag())))) {
            return false;
        }
        return true;
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

    private record RuntimeEventContext(
            String type,
            LivingEntity entity,
            LivingEntity target,
            Entity directEntity,
            ItemStack itemStack,
            BlockState blockState,
            double distanceMoved,
            double baseDamage
    ) {
        private boolean isProjectileAttack() {
            return directEntity != null && directEntity != entity;
        }
    }

    private record MoveResult(double remainder) {
    }

    private record PointUsage(long usedPoints) {
    }

    @FunctionalInterface
    private interface TagFactory<T> {
        TagKey<T> create(ResourceLocation id);
    }
}
