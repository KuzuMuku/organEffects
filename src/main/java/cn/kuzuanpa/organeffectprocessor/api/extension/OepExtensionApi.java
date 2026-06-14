package cn.kuzuanpa.organeffectprocessor.api.extension;

import cn.kuzuanpa.organeffectprocessor.api.EffectDefinition;
import cn.kuzuanpa.organeffectprocessor.common.capability.IEffectHolder;
import cn.kuzuanpa.organeffectprocessor.common.debug.OepDebug;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

public final class OepExtensionApi {
    private static final Map<String, PointProducer> POINT_PRODUCERS = new LinkedHashMap<>();
    private static final Map<String, PointExecutor> POINT_EXECUTORS = new LinkedHashMap<>();

    private OepExtensionApi() {
    }

    public static void registerPointProducer(PointProducer producer) {
        POINT_PRODUCERS.put(producer.id(), producer);
    }

    public static void registerPointExecutor(PointExecutor executor) {
        POINT_EXECUTORS.put(executor.type(), executor);
    }

    public static Collection<PointProducer> getPointProducers() {
        return List.copyOf(POINT_PRODUCERS.values());
    }

    public static PointExecutor getPointExecutor(String type) {
        return POINT_EXECUTORS.get(type);
    }

    public static void registerBuiltins() {
        registerPointExecutor(new GrantItemsExecutor());
        registerPointExecutor(new ApplyMobEffectExecutor());
        registerPointExecutor(new HealExecutor());
        registerPointExecutor(new TauntExecutor());
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
            double total = (action.amount() != null ? action.amount() : 0.0D)
                    + usage.usedPoints() * (action.amountPerPoint() != null ? action.amountPerPoint() : 0.0D);
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
