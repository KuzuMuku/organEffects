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

import cn.kuzuanpa.organeffects.common.capability.EffectCapabilities;
import cn.kuzuanpa.organeffects.common.capability.IEffectHolder;
import cn.kuzuanpa.organeffects.common.config.OrganEffectsServerConfig;
import cn.kuzuanpa.organeffects.common.network.OrganEffectsNetwork;
import cn.kuzuanpa.organeffects.common.registry.OrganEffectsEnchantments;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class OrganStatService {
    private static final String MODIFIER_NAME_PREFIX = "organeffects:organ_stat:";
    private static final String ORGAN_STAT_TYPE = "organ_stat:";

    private static final String OXYGEN_EFFICIENCY = key("oxygen_efficiency");
    private static final String CARDIAC_OUTPUT = key("cardiac_output");
    private static final String LIVER_DETOX = key("liver_detox_efficiency");
    private static final String KIDNEY_FILTRATION = key("kidney_filtration_efficiency");
    private static final String DIGESTIVE_ABSORPTION = key("digestive_absorption");
    private static final String IMMUNE_EFFICIENCY = key("immune_efficiency");
    private static final String NEURAL_CONDUCTION = key("neural_conduction");
    private static final String MUSCULAR_STRENGTH = key("muscular_strength");
    private static final String END_CRYSTAL_RESONANCE = key("end_crystal_resonance");
    private static final String ATTRIBUTE_ARMOR = "attribute:minecraft:generic.armor";
    private static final String ATTRIBUTE_ARMOR_TOUGHNESS = "attribute:minecraft:generic.armor_toughness";
    private static final String ATTRIBUTE_ATTACK_DAMAGE = "attribute:minecraft:generic.attack_damage";
    private static final String ATTRIBUTE_MOVEMENT_SPEED = "attribute:minecraft:generic.movement_speed";
    private static final String ATTRIBUTE_MAX_HEALTH = "attribute:minecraft:generic.max_health";
    private static final String MUSCULAR_STRENGTH_KEY = "organ_stat:organeffects:muscular_strength";
    private static final double END_CRYSTAL_HEAL_RANGE = 32.0D;

    private static final Map<UUID, PendingArrowBoost> PENDING_ARROW_BOOSTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Long>> CLIENT_PLAYER_POINTS = new ConcurrentHashMap<>();
    private static final Map<Integer, Map<String, Long>> CLIENT_ENTITY_POINTS = new ConcurrentHashMap<>();

    private OrganStatService() {
    }

    public static void apply(Player player, Map<String, Long> points) {
        applyAttributeModifier(player, Attributes.MAX_HEALTH, "cardiac_output", Math.max(0L, getStat(points, CARDIAC_OUTPUT)) * 0.5D,
                AttributeModifier.Operation.ADDITION);
        applyAttributeModifier(player, Attributes.ATTACK_DAMAGE, "muscular_strength", Math.max(0L, getStat(points, MUSCULAR_STRENGTH)) * 0.75D,
                AttributeModifier.Operation.ADDITION);
        applyAttributeModifier(player, Attributes.ATTACK_SPEED, "neural_conduction", Math.max(0L, getStat(points, NEURAL_CONDUCTION)) * 0.08D,
                AttributeModifier.Operation.ADDITION);

        double maxHealth = player.getMaxHealth();
        if (player.getHealth() > maxHealth) {
            player.setHealth((float) maxHealth);
        }
    }

    public static void applyNonPlayer(LivingEntity entity, Map<String, Long> points) {
        if (entity instanceof Player) {
            return;
        }

        double maxHealthBonus = Math.max(0L, getStat(points, CARDIAC_OUTPUT)) + Math.max(0L, getStat(points, ATTRIBUTE_MAX_HEALTH));
        double armorBonus = Math.max(0L, getStat(points, ATTRIBUTE_ARMOR));
        double armorToughnessBonus = Math.max(0L, getStat(points, ATTRIBUTE_ARMOR_TOUGHNESS));
        double attackDamageBonus = Math.max(0L, getStat(points, MUSCULAR_STRENGTH)) * 0.75D + Math.max(0L, getStat(points, ATTRIBUTE_ATTACK_DAMAGE));
        double movementSpeedBonus = Math.max(0L, getStat(points, ATTRIBUTE_MOVEMENT_SPEED)) * 0.1D
                + Math.max(0L, getStat(points, OXYGEN_EFFICIENCY)) * 0.02D
                + Math.max(0L, getStat(points, NEURAL_CONDUCTION)) * 0.02D;

        applyAttributeModifier(entity, Attributes.MAX_HEALTH, "non_player_cardiac_output", maxHealthBonus, AttributeModifier.Operation.ADDITION);
        applyAttributeModifier(entity, Attributes.ARMOR, "non_player_armor", armorBonus, AttributeModifier.Operation.ADDITION);
        applyAttributeModifier(entity, Attributes.ARMOR_TOUGHNESS, "non_player_armor_toughness", armorToughnessBonus, AttributeModifier.Operation.ADDITION);
        applyAttributeModifier(entity, Attributes.ATTACK_DAMAGE, "non_player_muscular_strength", attackDamageBonus, AttributeModifier.Operation.ADDITION);
        applyAttributeModifier(entity, Attributes.MOVEMENT_SPEED, "non_player_speed", movementSpeedBonus, AttributeModifier.Operation.ADDITION);

        double maxHealth = entity.getMaxHealth();
        if (entity.getHealth() > maxHealth) {
            entity.setHealth((float) maxHealth);
        }
    }

    public static void tick(Player player) {
        Map<String, Long> points = getPoints(player);
        if (points.isEmpty()) {
            return;
        }

        tickOxygen(player, points);
        tickCardiacRecovery(player, points);
        tickImmuneRecovery(player, points);
        tickDetox(player, points);
        tickKidneyPenalty(player, points);
        tickNeuralPenalty(player, points);
        tickEndCrystalResonance(player, points);
    }

    public static void tickNonPlayer(LivingEntity entity) {
        if (entity instanceof Player) {
            return;
        }
        Map<String, Long> points = getPoints(entity);
        if (points.isEmpty()) {
            return;
        }
        tickNonPlayerRecovery(entity, points);
        tickEndCrystalResonance(entity, points);
    }

    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        long neural = Math.max(0L, getStat(event.getEntity(), NEURAL_CONDUCTION));
        if (neural <= 0L) {
            return;
        }
        event.setNewSpeed((float) (event.getNewSpeed() * (1.0D + neural * 0.12D)));
    }

    public static void onArrowLoose(ArrowLooseEvent event) {
        if (!(event.getBow().getItem() instanceof BowItem) || event.getEntity().level().isClientSide()) {
            return;
        }
        long strength = getPositiveMuscularStrength(event.getEntity());
        if (strength <= 0L) {
            return;
        }

        event.setCharge(event.getCharge() + Math.toIntExact(strength * 2L));
        long expireAtTick = event.getEntity().level().getGameTime() + 2L;
        double velocityMultiplier = 1.0D + strength * 0.04D;
        int tensionLevel = event.getBow().getEnchantmentLevel(OrganEffectsEnchantments.TENSION.get());
        double maxVelocity = OrganEffectsServerConfig.getBowVelocityCap(event.getBow())
                + tensionLevel * OrganEffectsServerConfig.getVelocityCapBonusPerEnchantLevel();
        PENDING_ARROW_BOOSTS.put(event.getEntity().getUUID(), new PendingArrowBoost(expireAtTick, velocityMultiplier, maxVelocity));
    }

    public static long getPositiveMuscularStrength(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return Math.max(0L, getClientSyncedPoint(entity, MUSCULAR_STRENGTH_KEY));
        }
        return Math.max(0L, getStat(entity, MUSCULAR_STRENGTH));
    }

    public static float getAdjustedBowPullProgress(LivingEntity entity, ItemStack bowStack) {
        if (!(bowStack.getItem() instanceof BowItem) || entity.getUseItem() != bowStack) {
            return 0.0F;
        }
        int charge = bowStack.getUseDuration() - entity.getUseItemRemainingTicks();
        long strength = getPositiveMuscularStrength(entity);
        int adjustedFullDrawTicks = Math.max(1, 20 - Math.toIntExact(strength * 2L));
        return Mth.clamp((float) charge / (float) adjustedFullDrawTicks, 0.0F, 1.0F);
    }

    public static void onArrowJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.getOwner() instanceof Player player)) {
            return;
        }
        PendingArrowBoost boost = PENDING_ARROW_BOOSTS.get(player.getUUID());
        if (boost == null) {
            return;
        }
        if (event.getLevel().getGameTime() > boost.expireAtTick()) {
            PENDING_ARROW_BOOSTS.remove(player.getUUID());
            return;
        }
        Vec3 boostedVelocity = arrow.getDeltaMovement().scale(boost.velocityMultiplier());
        double maxVelocity = Math.max(0.0D, boost.maxVelocity());
        if (maxVelocity > 0.0D && boostedVelocity.lengthSqr() > maxVelocity * maxVelocity) {
            boostedVelocity = boostedVelocity.normalize().scale(maxVelocity);
        }
        arrow.setDeltaMovement(boostedVelocity);
        PENDING_ARROW_BOOSTS.remove(player.getUUID());
    }

    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem();
        FoodProperties food = stack.getFoodProperties(player);
        if (food == null) {
            return;
        }
        long digestive = Math.max(0L, getStat(player, DIGESTIVE_ABSORPTION));
        if (digestive <= 0L) {
            return;
        }
        int extraNutrition = (int) (digestive / 2L);
        float extraSaturation = (float) (digestive * 0.1D);
        if (extraNutrition > 0 || extraSaturation > 0.0F) {
            player.getFoodData().eat(extraNutrition, extraSaturation);
        }
    }

    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        MobEffectInstance effectInstance = event.getEffectInstance();
        if (effectInstance == null || effectInstance.getEffect().getCategory() != MobEffectCategory.HARMFUL) {
            return;
        }
        long immune = Math.max(0L, getStat(event.getEntity(), IMMUNE_EFFICIENCY));
        if (immune <= 0L) {
            return;
        }
        double resistChance = Math.min(0.75D, immune * 0.12D);
        if (event.getEntity().getRandom().nextDouble() < resistChance) {
            event.setResult(MobEffectEvent.Applicable.Result.DENY);
        }
    }

    public static void clearTransientState(Player player) {
        PENDING_ARROW_BOOSTS.remove(player.getUUID());
        CLIENT_PLAYER_POINTS.remove(player.getUUID());
    }

    public static void syncPlayerPoints(UUID playerId, Map<String, Long> points) {
        if (points == null || points.isEmpty()) {
            CLIENT_PLAYER_POINTS.remove(playerId);
            return;
        }
        CLIENT_PLAYER_POINTS.put(playerId, new ConcurrentHashMap<>(points));
    }

    public static void syncEntityPoints(int entityId, Map<String, Long> points) {
        if (points == null || points.isEmpty()) {
            CLIENT_ENTITY_POINTS.remove(entityId);
            return;
        }
        CLIENT_ENTITY_POINTS.put(entityId, new ConcurrentHashMap<>(points));
    }

    public static Map<String, Long> getClientSyncedPoints(LivingEntity entity) {
        if (entity == null) {
            return Map.of();
        }
        if (entity instanceof Player player) {
            return Map.copyOf(CLIENT_PLAYER_POINTS.getOrDefault(player.getUUID(), Map.of()));
        }
        return Map.copyOf(CLIENT_ENTITY_POINTS.getOrDefault(entity.getId(), Map.of()));
    }

    public static long getClientSyncedPoint(LivingEntity entity, String pointKey) {
        if (entity == null || pointKey == null || pointKey.isBlank()) {
            return 0L;
        }
        if (entity instanceof Player player) {
            return CLIENT_PLAYER_POINTS.getOrDefault(player.getUUID(), Map.of()).getOrDefault(pointKey, 0L);
        }
        return CLIENT_ENTITY_POINTS.getOrDefault(entity.getId(), Map.of()).getOrDefault(pointKey, 0L);
    }

    public static Set<Integer> getClientSyncedEntityIds() {
        return Set.copyOf(CLIENT_ENTITY_POINTS.keySet());
    }

    private static void tickOxygen(Player player, Map<String, Long> points) {
        long oxygen = Math.max(0L, getStat(points, OXYGEN_EFFICIENCY));
        if (oxygen <= 0L || player.tickCount % 10 != 0) {
            return;
        }
        int currentAir = player.getAirSupply();
        int maxAir = player.getMaxAirSupply();
        if (currentAir >= maxAir) {
            return;
        }
        int airBonus = 1 + (int) (oxygen / 3L);
        player.setAirSupply(Math.min(maxAir, currentAir + airBonus));
    }

    private static void tickCardiacRecovery(Player player, Map<String, Long> points) {
        long cardiac = Math.max(0L, getStat(points, CARDIAC_OUTPUT));
        if (cardiac <= 0L || !canRecover(player)) {
            return;
        }
        int interval = Math.max(40, 200 - (int) cardiac * 15);
        if (player.tickCount % interval == 0) {
            player.heal(1.0F);
        }
    }

    private static void tickImmuneRecovery(Player player, Map<String, Long> points) {
        long immune = Math.max(0L, getStat(points, IMMUNE_EFFICIENCY));
        if (immune <= 0L || !canRecover(player) || hasHarmfulEffect(player)) {
            return;
        }
        int interval = Math.max(60, 240 - (int) immune * 12);
        if (player.tickCount % interval == 0) {
            player.heal(0.5F);
        }
    }

    private static void tickDetox(Player player, Map<String, Long> points) {
        if (player.tickCount % 20 != 0) {
            return;
        }
        long liver = Math.max(0L, getStat(points, LIVER_DETOX));
        long kidney = Math.max(0L, getStat(points, KIDNEY_FILTRATION));
        long immune = Math.max(0L, getStat(points, IMMUNE_EFFICIENCY));
        if (liver <= 0L && kidney <= 0L && immune <= 0L) {
            return;
        }

        for (MobEffectInstance effectInstance : List.copyOf(player.getActiveEffects())) {
            MobEffect effect = effectInstance.getEffect();
            if (effect.getCategory() != MobEffectCategory.HARMFUL) {
                continue;
            }

            int reduction = 0;
            if (liver > 0L) {
                reduction += 1 + (int) liver;
            }
            if (immune > 0L) {
                reduction += (int) immune;
            }
            if ((effect == MobEffects.POISON || effect == MobEffects.WITHER) && kidney > 0L) {
                reduction += 1 + (int) kidney * 2;
            }
            if (reduction <= 0) {
                continue;
            }
            reduceEffectDuration(player, effectInstance, reduction);
        }
    }

    private static void tickKidneyPenalty(Player player, Map<String, Long> points) {
        long kidney = getStat(points, KIDNEY_FILTRATION);
        if (kidney >= 0L || player.tickCount % 100 != 0 || player.hasEffect(MobEffects.POISON)) {
            return;
        }
        int amplifier = Mth.clamp((int) (-kidney - 1L), 0, 2);
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 80, amplifier, false, true, true));
    }

    private static void tickNeuralPenalty(Player player, Map<String, Long> points) {
        long neural = getStat(points, NEURAL_CONDUCTION);
        if (neural >= 0L || player.tickCount % 20 != 0) {
            return;
        }
        int severity = Mth.clamp((int) (-neural), 0, 4);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, severity, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, severity, false, false, true));
    }

    private static void tickNonPlayerRecovery(LivingEntity entity, Map<String, Long> points) {
        if (!entity.isAlive() || entity.getHealth() >= entity.getMaxHealth()) {
            return;
        }
        long recovery = Math.max(0L, getStat(points, CARDIAC_OUTPUT))
                + Math.max(0L, getStat(points, IMMUNE_EFFICIENCY))
                + Math.max(0L, getStat(points, DIGESTIVE_ABSORPTION));
        if (recovery <= 0L) {
            return;
        }
        int interval = Math.max(40, 220 - (int) recovery * 15);
        if (entity.tickCount % interval == 0) {
            entity.heal(1.0F);
        }
    }

    private static void tickEndCrystalResonance(LivingEntity entity, Map<String, Long> points) {
        long resonance = Math.max(0L, getStat(points, END_CRYSTAL_RESONANCE));
        if (resonance <= 0L || !entity.isAlive() || entity.getHealth() >= entity.getMaxHealth() || entity.tickCount % 10 != 0) {
            return;
        }
        EndCrystal crystal = entity.level().getEntitiesOfClass(EndCrystal.class, entity.getBoundingBox().inflate(END_CRYSTAL_HEAL_RANGE), EndCrystal::isAlive)
                .stream()
                .min(java.util.Comparator.comparingDouble(entity::distanceToSqr))
                .orElse(null);
        if (crystal == null) {
            return;
        }

        entity.heal(Math.min(1.0F + resonance * 0.5F, 3.0F));
        OrganEffectsNetwork.sendBeamEffect(crystal, OrganEffectsNetwork.BeamEffectKind.END_CRYSTAL, entity, 16);
    }

    private static boolean canRecover(Player player) {
        return player.isAlive() && player.getHealth() < player.getMaxHealth() && player.getFoodData().getFoodLevel() >= 6;
    }

    private static boolean hasHarmfulEffect(LivingEntity entity) {
        for (MobEffectInstance effectInstance : entity.getActiveEffects()) {
            if (effectInstance.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                return true;
            }
        }
        return false;
    }

    private static void reduceEffectDuration(LivingEntity entity, MobEffectInstance effectInstance, int reduction) {
        MobEffect effect = effectInstance.getEffect();
        int newDuration = effectInstance.getDuration() - reduction;
        if (newDuration <= 0) {
            entity.removeEffect(effect);
            return;
        }
        entity.removeEffect(effect);
        entity.addEffect(new MobEffectInstance(effect, newDuration, effectInstance.getAmplifier(), effectInstance.isAmbient(),
                effectInstance.isVisible(), effectInstance.showIcon()));
    }

    private static long getStat(Player player, String pointKey) {
        return getStat(getPoints(player), pointKey);
    }

    private static long getStat(LivingEntity entity, String pointKey) {
        IEffectHolder holder = entity.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        return holder == null ? 0L : getStat(holder.getEffectPoints(), pointKey);
    }

    private static long getStat(Map<String, Long> points, String pointKey) {
        return points.getOrDefault(pointKey, 0L);
    }

    private static Map<String, Long> getPoints(Player player) {
        IEffectHolder holder = player.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        return holder == null ? Map.of() : holder.getEffectPoints();
    }

    private static Map<String, Long> getPoints(LivingEntity entity) {
        IEffectHolder holder = entity.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        return holder == null ? Map.of() : holder.getEffectPoints();
    }

    private static void applyAttributeModifier(LivingEntity entity, Attribute attribute, String modifierName, double amount,
                                               AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        UUID modifierId = UUID.nameUUIDFromBytes((MODIFIER_NAME_PREFIX + modifierName).getBytes(StandardCharsets.UTF_8));
        instance.removeModifier(modifierId);
        if (amount == 0.0D) {
            return;
        }
        instance.addPermanentModifier(new AttributeModifier(modifierId, MODIFIER_NAME_PREFIX + modifierName, amount, operation));
    }

    private static String key(String path) {
        return ORGAN_STAT_TYPE + ResourceLocation.fromNamespaceAndPath("organeffects", path);
    }

    private record PendingArrowBoost(long expireAtTick, double velocityMultiplier, double maxVelocity) {
    }
}
