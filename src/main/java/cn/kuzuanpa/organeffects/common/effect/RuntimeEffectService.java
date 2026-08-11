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

import com.google.gson.JsonObject;
import cn.kuzuanpa.organapi.api.query.OrganPosition;
import cn.kuzuanpa.organapi.api.query.OrganQueryService;
import cn.kuzuanpa.organapi.common.data.OrganRegistryAccess;
import cn.kuzuanpa.organeffects.api.EffectDefinition;
import cn.kuzuanpa.organeffects.api.extension.OrganEffectsExtensionApi;
import cn.kuzuanpa.organeffects.api.extension.OrganEffectsRuntimeEvent;
import cn.kuzuanpa.organeffects.common.capability.EffectCapabilities;
import cn.kuzuanpa.organeffects.common.capability.IEffectHolder;
import cn.kuzuanpa.organeffects.common.data.OrganEffectData;
import cn.kuzuanpa.organeffects.common.debug.OrganEffectsDebug;
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
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

public final class RuntimeEffectService {
    private static final Map<UUID, Double> MOVE_REMAINDERS = new HashMap<>();
    private static final Map<UUID, TauntState> TAUNT_STATES = new HashMap<>();

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
        fireEvent(entity, OrganEffectsRuntimeEvent.builder("eat", entity).itemStack(stack).build());
    }

    public static void handleMine(Player player, BlockState state) {
        fireEvent(player, OrganEffectsRuntimeEvent.builder("mine", player).blockState(state).build());
    }

    public static void handleUseItem(Player player, ItemStack stack) {
        fireEvent(player, OrganEffectsRuntimeEvent.builder("use_item", player).itemStack(stack).build());
    }

    public static void handleAttacked(LivingEntity victim, LivingEntity attacker, Entity directEntity, float damageAmount) {
        fireEvent(victim, OrganEffectsRuntimeEvent.builder("attacked", victim)
                .target(attacker)
                .directEntity(directEntity)
                .amount(damageAmount)
                .build());
    }

    public static void handleHealthLoss(LivingEntity entity, Entity sourceEntity, Entity directEntity, float damageAmount) {
        LivingEntity attacker = sourceEntity instanceof LivingEntity living ? living : null;
        fireEvent(entity, OrganEffectsRuntimeEvent.builder("health_loss", entity)
                .target(attacker)
                .directEntity(directEntity)
                .amount(damageAmount)
                .build());
    }

    public static void handleKill(LivingEntity attacker, LivingEntity target, Entity directEntity) {
        fireEvent(attacker, OrganEffectsRuntimeEvent.builder("kill", attacker)
                .target(target)
                .directEntity(directEntity)
                .build());
    }

    public static void handleBiomeChange(Player player) {
        fireEvent(player, OrganEffectsRuntimeEvent.builder("biome_change", player).build());
    }

    public static void handleDimensionChange(Player player) {
        fireEvent(player, OrganEffectsRuntimeEvent.builder("dimension_change", player).build());
    }

    public static void handleAttack(LivingEntity attacker, LivingEntity target, Entity directEntity, float baseDamage) {
        fireEvent(attacker, OrganEffectsRuntimeEvent.builder("attack", attacker)
                .target(target)
                .directEntity(directEntity)
                .amount(baseDamage)
                .build());
    }

    public static void handleJump(LivingEntity entity) {
        fireEvent(entity, OrganEffectsRuntimeEvent.builder("jump", entity).build());
    }

    public static void handleLand(LivingEntity entity) {
        fireEvent(entity, OrganEffectsRuntimeEvent.builder("land", entity).build());
    }

    public static void handleSprintStart(Player player) {
        fireEvent(player, OrganEffectsRuntimeEvent.builder("sprint_start", player).build());
    }

    public static void handleSprintStop(Player player) {
        fireEvent(player, OrganEffectsRuntimeEvent.builder("sprint_stop", player).build());
    }

    public static void handleSneakStart(Player player) {
        fireEvent(player, OrganEffectsRuntimeEvent.builder("sneak_start", player).build());
    }

    public static void handleSneakStop(Player player) {
        fireEvent(player, OrganEffectsRuntimeEvent.builder("sneak_stop", player).build());
    }

    public static void handleSwimStart(LivingEntity entity) {
        fireEvent(entity, OrganEffectsRuntimeEvent.builder("swim_start", entity).build());
    }

    public static void handleSwimStop(LivingEntity entity) {
        fireEvent(entity, OrganEffectsRuntimeEvent.builder("swim_stop", entity).build());
    }

    public static void handleEnterWater(LivingEntity entity) {
        fireEvent(entity, OrganEffectsRuntimeEvent.builder("enter_water", entity).build());
    }

    public static void handleLeaveWater(LivingEntity entity) {
        fireEvent(entity, OrganEffectsRuntimeEvent.builder("leave_water", entity).build());
    }

    public static void handleTakeDamage(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float damageAmount) {
        fireEvent(entity, OrganEffectsRuntimeEvent.builder("take_damage", entity)
                .amount(damageAmount)
                .extra(buildDamageExtra(source))
                .build());
    }

    public static void handleDealDamage(LivingEntity attacker, LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float damageAmount) {
        fireEvent(attacker, OrganEffectsRuntimeEvent.builder("deal_damage", attacker)
                .target(target)
                .amount(damageAmount)
                .extra(buildDamageExtra(source))
                .build());
    }

    public static void handleProjectileHit(LivingEntity shooter, Entity projectile, Entity hitEntity) {
        fireEvent(shooter, OrganEffectsRuntimeEvent.builder("projectile_hit", shooter)
                .directEntity(projectile)
                .target(hitEntity instanceof LivingEntity living ? living : null)
                .build());
    }

    public static void handleBlockPlace(Player player, BlockState state, net.minecraft.core.BlockPos pos) {
        fireEvent(player, OrganEffectsRuntimeEvent.builder("block_place", player)
                .blockState(state)
                .extra(new JsonObject())
                .build());
    }

    public static void handleItemCraft(Player player, ItemStack stack) {
        fireEvent(player, OrganEffectsRuntimeEvent.builder("item_craft", player).itemStack(stack).build());
    }

    public static void handleItemSmelt(Player player, ItemStack stack) {
        fireEvent(player, OrganEffectsRuntimeEvent.builder("item_smelt", player).itemStack(stack).build());
    }

    public static void handleItemRepair(Player player, ItemStack stack) {
        fireEvent(player, OrganEffectsRuntimeEvent.builder("item_repair", player).itemStack(stack).build());
    }

    public static void handleItemEnchant(Player player, ItemStack stack) {
        fireEvent(player, OrganEffectsRuntimeEvent.builder("item_enchant", player).itemStack(stack).build());
    }

    public static void handleFishCatch(Player player, ItemStack stack) {
        fireEvent(player, OrganEffectsRuntimeEvent.builder("fish_catch", player).itemStack(stack).build());
    }

    public static void handleSleep(Player player) {
        fireEvent(player, OrganEffectsRuntimeEvent.builder("sleep", player).build());
    }

    public static void handleRespawn(Player player) {
        fireEvent(player, OrganEffectsRuntimeEvent.builder("respawn", player).build());
    }

    public static void handleConsumeItem(LivingEntity entity, ItemStack stack) {
        fireEvent(entity, OrganEffectsRuntimeEvent.builder("consume_item", entity).itemStack(stack).build());
    }

    public static void handleEquipItem(LivingEntity entity, ItemStack stack, EquipmentSlot slot) {
        JsonObject extra = new JsonObject();
        extra.addProperty("slot", slot.getName());
        fireEvent(entity, OrganEffectsRuntimeEvent.builder("equip_item", entity).itemStack(stack).extra(extra).build());
    }

    public static void handleUnequipItem(LivingEntity entity, ItemStack stack, EquipmentSlot slot) {
        JsonObject extra = new JsonObject();
        extra.addProperty("slot", slot.getName());
        fireEvent(entity, OrganEffectsRuntimeEvent.builder("unequip_item", entity).itemStack(stack).extra(extra).build());
    }

    public static void handleCriticalHit(Player player, LivingEntity target, float damage) {
        fireEvent(player, OrganEffectsRuntimeEvent.builder("critical_hit", player).target(target).amount(damage).build());
    }

    public static void handleShieldBlock(LivingEntity entity, Entity sourceEntity, float damage) {
        LivingEntity attacker = sourceEntity instanceof LivingEntity living ? living : null;
        fireEvent(entity, OrganEffectsRuntimeEvent.builder("shield_block", entity).target(attacker).amount(damage).build());
        fireEvent(entity, OrganEffectsRuntimeEvent.builder("parry", entity).target(attacker).amount(damage).build());
    }

    public static void registerTaunt(Player player, double radius, long durationTicks, long refreshTicks, String target) {
        if (!shouldApplyTaunt(target)) {
            return;
        }
        long now = player.level().getGameTime();
        long expiresAtTick = now + Math.max(1L, durationTicks);
        long effectiveRefreshTicks = Math.max(1L, refreshTicks);
        String normalizedTarget = normalizeTauntTarget(target);

        TauntState state = TAUNT_STATES.get(player.getUUID());
        if (state == null) {
            TAUNT_STATES.put(player.getUUID(), new TauntState(expiresAtTick, now + effectiveRefreshTicks, radius, effectiveRefreshTicks, normalizedTarget));
        } else {
            state.expiresAtTick = Math.max(state.expiresAtTick, expiresAtTick);
            state.nextRefreshTick = now + effectiveRefreshTicks;
            state.radius = Math.max(state.radius, radius);
            state.refreshTicks = effectiveRefreshTicks;
            state.target = normalizedTarget;
        }
        applyTaunt(player, radius, normalizedTarget);
    }

    public static void tick(Player player) {
        TauntState state = TAUNT_STATES.get(player.getUUID());
        if (state == null) {
            handleTickEvent(player);
            return;
        }
        long now = player.level().getGameTime();
        if (now >= state.expiresAtTick) {
            TAUNT_STATES.remove(player.getUUID());
            handleTickEvent(player);
            return;
        }
        if (now >= state.nextRefreshTick) {
            applyTaunt(player, state.radius, state.target);
            state.nextRefreshTick = now + state.refreshTicks;
        }
        handleTickEvent(player);
    }

    public static void clearTransientState(Player player) {
        TAUNT_STATES.remove(player.getUUID());
    }

    public static void fireEvent(LivingEntity entity, OrganEffectsRuntimeEvent event) {
        handleEvent(entity, event);
    }

    private static void handleTickEvent(Player player) {
        fireEvent(player, OrganEffectsRuntimeEvent.builder("tick", player).build());
    }

    private static void applyTaunt(Player player, double radius, String target) {
        if (!shouldApplyTaunt(target)) {
            return;
        }
        AABB area = player.getBoundingBox().inflate(radius);
        List<Mob> mobs = player.level().getEntitiesOfClass(Mob.class, area, mob -> mob instanceof Enemy
                && mob.isAlive()
                && EntitySelector.NO_SPECTATORS.test(mob)
                && mob.canAttack(player));
        for (Mob mob : mobs) {
            if (!mob.getSensing().hasLineOfSight(player) && !TargetingConditions.DEFAULT.test(mob, player)) {
                continue;
            }
            mob.setTarget(player);
            mob.setLastHurtByMob(player);
        }
    }

    private static boolean shouldApplyTaunt(String target) {
        return target == null || target.isBlank() || "hostile".equals(target);
    }

    private static String normalizeTauntTarget(String target) {
        return target == null || target.isBlank() ? "hostile" : target;
    }

    private static JsonObject buildDamageExtra(net.minecraft.world.damagesource.DamageSource source) {
        JsonObject extra = new JsonObject();
        if (source == null) {
            return extra;
        }
        try {
            extra.addProperty("damage_type", source.getMsgId());
        } catch (Exception ignored) {
        }
        return extra;
    }

    private static JsonObject buildMoveExtra(Player player) {
        JsonObject extra = new JsonObject();
        if (player == null) {
            return extra;
        }
        try {
            extra.addProperty("sprinting", player.isSprinting());
            extra.addProperty("sneaking", player.isCrouching());
            extra.addProperty("swimming", player.isSwimming());
            //elytra flying
            extra.addProperty("fall_flying", player.isFallFlying());
        } catch (Exception ignored) {
        }
        return extra;
    }
    //todo: 提取通用的“每做某事X次，将获得点数乘以Y”处理器
    private static MoveResult handleMoveEvent(Player player, double totalDistance) {
        IEffectHolder holder = player.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder == null) {
            return new MoveResult(0.0D);
        }
        double maxConsumedDistance = 0.0D;

        OrganEffectsRuntimeEvent event = OrganEffectsRuntimeEvent.builder("move", player)
                .distanceMoved(totalDistance)
                .extra(buildMoveExtra(player))
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

    private static void handleEvent(Entity entity, OrganEffectsRuntimeEvent event) {
        IEffectHolder holder = entity.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder == null) {
            return;
        }
        for (EffectInstance instance : collectMatchingEffects(entity, event.type())) {
            boolean conditionsMatch = EffectRecalculationService.evaluateConditions(entity, instance.position(), instance.effect().conditions());
            if (entity instanceof Player player) {
                OrganEffectsDebug.trace(player, "event %s organ=%s slot=%s#%d conditions=%s", event.type(), instance.organId(), instance.position().bodyPartId(), instance.position().slotIndex(), conditionsMatch);
            }
            if (!conditionsMatch) {
                continue;
            }
            for (EffectDefinition.EventRule eventRule : instance.effect().events()) {
                boolean filterMatch = event.type().equals(eventRule.type()) && matchesEventFilter(eventRule, event);
                if (filterMatch && "tick".equals(event.type()) && !matchesTickInterval(entity, eventRule)) {
                    filterMatch = false;
                }
                if (entity instanceof Player player) {
                    OrganEffectsDebug.trace(player, "event rule %s matched=%s", eventRule.type(), filterMatch);
                }
                if (!filterMatch) {
                    continue;
                }
                applyPointMutations(entity, holder, instance, eventRule.addPoints(), 1L);
                applyConsumes(entity, holder, instance, eventRule.consumePoints());
                if (entity instanceof Player player) {
                    TargetPointExecutionService.handleEvent(player, holder, event);
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
                    OrganEffectsDebug.trace(player, "mutation skipped %s:%s attempts=%d", mutation.type(), mutation.id(), multiplier);
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
                    OrganEffectsDebug.trace(player, "+runtime %s %d -> %d expires@%d", pointKey, before, after, expireAtTick);
                }
            } else {
                String source = resolveSource(instance, mutation.source());
                long before = holder.getPointsForSource(source).getOrDefault(pointKey, 0L);
                long after = holder.addSourcePoint(source, pointKey, totalAmount);
                if (entity instanceof Player player) {
                    OrganEffectsDebug.trace(player, "+source %s %s %d -> %d", source, pointKey, before, after);
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
                    OrganEffectsDebug.trace(player, "consume runtime %s used=%d %d -> %d", pointKey, used, before, after);
                }
            } else {
                long before = holder.getPooledSourcePoints(pointKey, mutation.source());
                long used = holder.consumePooledSourcePoints(pointKey, mutation.source(), amount);
                long after = holder.getPooledSourcePoints(pointKey, mutation.source());
                if (entity instanceof Player player) {
                    OrganEffectsDebug.trace(player, "consume pooled source=%s %s used=%d %d -> %d", mutation.source(), pointKey, used, before, after);
                }
            }
        }
    }

    //todo: 提取抽象类
    private static boolean matchesEventFilter(EffectDefinition.EventRule eventRule, OrganEffectsRuntimeEvent event) {
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
        return OrganEffectsExtensionApi.matchesExtraEventFilters(eventRule, event);
    }

    private static boolean matchesTickInterval(Entity entity, EffectDefinition.EventRule eventRule) {
        long interval = 1L;
        Long configured = eventRule.configLong("interval_ticks");
        if (configured == null) {
            configured = eventRule.configLong("interval");
        }
        if (configured != null) {
            interval = Math.max(1L, configured);
        }
        return entity.tickCount % interval == 0L;
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

    private static final class TauntState {
        private long expiresAtTick;
        private long nextRefreshTick;
        private double radius;
        private long refreshTicks;
        private String target;

        private TauntState(long expiresAtTick, long nextRefreshTick, double radius, long refreshTicks, String target) {
            this.expiresAtTick = expiresAtTick;
            this.nextRefreshTick = nextRefreshTick;
            this.radius = radius;
            this.refreshTicks = refreshTicks;
            this.target = target;
        }
    }

    @FunctionalInterface
    private interface TagFactory<T> {
        TagKey<T> create(ResourceLocation id);
    }
}
