package cn.kuzuanpa.organeffectprocessor.common.event;

import cn.kuzuanpa.organapi.api.event.OrganStateCommittedEvent;
import cn.kuzuanpa.organeffectprocessor.common.capability.EffectHolderProvider;
import cn.kuzuanpa.organeffectprocessor.common.capability.EffectCapabilities;
import cn.kuzuanpa.organeffectprocessor.common.capability.IEffectHolder;
import cn.kuzuanpa.organeffectprocessor.common.debug.OepDebug;
import cn.kuzuanpa.organeffectprocessor.common.effect.EffectRecalculationService;
import cn.kuzuanpa.organeffectprocessor.common.effect.OrganStatService;
import cn.kuzuanpa.organeffectprocessor.common.effect.RuntimeEffectService;
import cn.kuzuanpa.organeffectprocessor.common.effect.RuntimePointExecutor;
import cn.kuzuanpa.organeffectprocessor.common.skill.SkillManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ServerEventHandler {
    private static final int DYNAMIC_RECOMPUTE_INTERVAL = 5;

    private final Map<UUID, Vec3> lastPositions = new HashMap<>();
    private final Map<UUID, ResourceKey<Biome>> lastBiomes = new HashMap<>();
    private final Map<UUID, Boolean> lastSprinting = new HashMap<>();
    private final Map<UUID, Boolean> lastSneaking = new HashMap<>();
    private final Map<UUID, Boolean> lastSwimming = new HashMap<>();
    private final Map<UUID, Boolean> lastOnGround = new HashMap<>();
    private final Map<UUID, Boolean> lastInWater = new HashMap<>();

    @SubscribeEvent
    public void onOrganStateCommitted(OrganStateCommittedEvent event) {
        if (!event.wasDirty()) {
            return;
        }
        EffectRecalculationService.recompute(event.getTarget());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        RuntimeEffectService.clearTransientState(event.getEntity());
        OrganStatService.clearTransientState(event.getEntity());
        SkillManager.clearTransientState(event.getEntity());
        EffectRecalculationService.recompute(event.getEntity());
        cachePlayerState(event.getEntity());
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        RuntimeEffectService.clearTransientState(event.getEntity());
        OrganStatService.clearTransientState(event.getEntity());
        SkillManager.clearTransientState(event.getEntity());
        EffectRecalculationService.recompute(event.getEntity());
        cachePlayerState(event.getEntity());
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        OrganStatService.onArrowJoinLevel(event);
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        EffectRecalculationService.reapply(player);
        cachePlayerState(player);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        Player player = event.player;
        IEffectHolder holder = player.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder != null) {
            holder.clearExpiredSkillCooldowns(player.level().getGameTime());
        }
        SkillManager.tickActiveSkills(player);
        RuntimeEffectService.tick(player);
        OrganStatService.tick(player);
        if (player.tickCount % DYNAMIC_RECOMPUTE_INTERVAL == 0) {
            EffectRecalculationService.recompute(player);
        } else {
            RuntimePointExecutor.execute(player);
        }
        Vec3 current = player.position();
        Vec3 previous = lastPositions.put(player.getUUID(), current);
        if (previous != null) {
            double horizontalDistance = current.subtract(previous).horizontalDistance();
            if (horizontalDistance > 0.0D) {
                RuntimeEffectService.handleMove(player, horizontalDistance);
            }
        }
        ResourceKey<Biome> currentBiome = getBiomeKey(player);
        ResourceKey<Biome> previousBiome = lastBiomes.put(player.getUUID(), currentBiome);
        if (previousBiome != null && currentBiome != null && !previousBiome.equals(currentBiome)) {
            RuntimeEffectService.handleBiomeChange(player);
        }

        boolean sprinting = player.isSprinting();
        boolean sneaking = player.isCrouching();
        boolean swimming = player.isSwimming();
        boolean onGround = player.onGround();
        boolean inWater = player.isInWater();
        Boolean previousSprinting = lastSprinting.put(player.getUUID(), sprinting);
        Boolean previousSneaking = lastSneaking.put(player.getUUID(), sneaking);
        Boolean previousSwimming = lastSwimming.put(player.getUUID(), swimming);
        Boolean previousOnGround = lastOnGround.put(player.getUUID(), onGround);
        Boolean previousInWater = lastInWater.put(player.getUUID(), inWater);
        if (Boolean.FALSE.equals(previousSprinting) && sprinting) {
            RuntimeEffectService.handleSprintStart(player);
        } else if (Boolean.TRUE.equals(previousSprinting) && !sprinting) {
            RuntimeEffectService.handleSprintStop(player);
        }
        if (Boolean.FALSE.equals(previousSneaking) && sneaking) {
            RuntimeEffectService.handleSneakStart(player);
        } else if (Boolean.TRUE.equals(previousSneaking) && !sneaking) {
            RuntimeEffectService.handleSneakStop(player);
        }
        if (Boolean.FALSE.equals(previousSwimming) && swimming) {
            RuntimeEffectService.handleSwimStart(player);
        } else if (Boolean.TRUE.equals(previousSwimming) && !swimming) {
            RuntimeEffectService.handleSwimStop(player);
        }
        if (Boolean.FALSE.equals(previousInWater) && inWater) {
            RuntimeEffectService.handleEnterWater(player);
        } else if (Boolean.TRUE.equals(previousInWater) && !inWater) {
            RuntimeEffectService.handleLeaveWater(player);
        }
        if (Boolean.FALSE.equals(previousOnGround) && onGround) {
            RuntimeEffectService.handleLand(player);
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        Entity attackerEntity = event.getSource().getEntity();
        RuntimeEffectService.handleTakeDamage(event.getEntity(), event.getSource(), event.getAmount());
        if (attackerEntity instanceof LivingEntity attacker) {
            RuntimeEffectService.handleAttack(attacker, event.getEntity(), event.getSource().getDirectEntity(), event.getAmount());
            RuntimeEffectService.handleAttacked(event.getEntity(), attacker, event.getSource().getDirectEntity(), event.getAmount());
            RuntimeEffectService.handleDealDamage(attacker, event.getEntity(), event.getSource(), event.getAmount());
        }
        RuntimeEffectService.handleHealthLoss(event.getEntity(), attackerEntity, event.getSource().getDirectEntity(), event.getAmount());
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        Entity attackerEntity = event.getSource().getEntity();
        if (attackerEntity instanceof LivingEntity attacker) {
            RuntimeEffectService.handleKill(attacker, event.getEntity(), event.getSource().getDirectEntity());
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        RuntimeEffectService.handleDimensionChange(event.getEntity());
        cachePlayerState(event.getEntity());
    }

    @SubscribeEvent
    public void onLivingJump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        RuntimeEffectService.handleJump(event.getEntity());
    }

    @SubscribeEvent
    public void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide() || event.getEntity() instanceof Player) {
            return;
        }
        OrganStatService.tickNonPlayer(event.getEntity());
    }

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        OrganStatService.onBreakSpeed(event);
    }

    @SubscribeEvent
    public void onArrowLoose(net.minecraftforge.event.entity.player.ArrowLooseEvent event) {
        OrganStatService.onArrowLoose(event);
    }

    @SubscribeEvent
    public void onProjectileImpact(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult entityHit && event.getProjectile().getOwner() instanceof LivingEntity owner) {
            Entity hitEntity = entityHit.getEntity();
            RuntimeEffectService.handleProjectileHit(owner, event.getProjectile(), hitEntity);
        }
    }

    @SubscribeEvent
    public void onCriticalHit(net.minecraftforge.event.entity.player.CriticalHitEvent event) {
        Player player = event.getEntity();
        if (event.getTarget() instanceof LivingEntity target) {
            RuntimeEffectService.handleCriticalHit(player, target, event.getDamageModifier());
        }
    }

    @SubscribeEvent
    public void onShieldBlock(net.minecraftforge.event.entity.living.ShieldBlockEvent event) {
        RuntimeEffectService.handleShieldBlock(event.getEntity(), event.getDamageSource().getEntity(), event.getBlockedDamage());
    }

    @SubscribeEvent
    public void onEquipmentChange(net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent event) {
        RuntimeEffectService.handleUnequipItem(event.getEntity(), event.getFrom(), event.getSlot());
        RuntimeEffectService.handleEquipItem(event.getEntity(), event.getTo(), event.getSlot());
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            RuntimeEffectService.handleBlockPlace(player, event.getPlacedBlock(), event.getPos());
        }
    }

    @SubscribeEvent
    public void onItemCraft(net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent event) {
        RuntimeEffectService.handleItemCraft(event.getEntity(), event.getCrafting());
    }

    @SubscribeEvent
    public void onItemSmelt(net.minecraftforge.event.entity.player.PlayerEvent.ItemSmeltedEvent event) {
        RuntimeEffectService.handleItemSmelt(event.getEntity(), event.getSmelting());
    }

    @SubscribeEvent
    public void onPlayerRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        RuntimeEffectService.handleRespawn(event.getEntity());
        RuntimeEffectService.clearTransientState(event.getEntity());
        OrganStatService.clearTransientState(event.getEntity());
        SkillManager.clearTransientState(event.getEntity());
        cachePlayerState(event.getEntity());
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        RuntimeEffectService.clearTransientState(event.getEntity());
        OrganStatService.clearTransientState(event.getEntity());
        SkillManager.clearTransientState(event.getEntity());
    }

    @SubscribeEvent
    public void onPlayerSleep(net.minecraftforge.event.entity.player.PlayerSleepInBedEvent event) {
        RuntimeEffectService.handleSleep(event.getEntity());
    }

    @SubscribeEvent
    public void onItemFished(net.minecraftforge.event.entity.player.ItemFishedEvent event) {
        RuntimeEffectService.handleFishCatch(event.getEntity(), event.getDrops().isEmpty() ? ItemStack.EMPTY : event.getDrops().get(0));
    }

    @SubscribeEvent
    public void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        OrganStatService.onUseItemFinish(event);
        ItemStack stack = event.getItem();
        if (!stack.isEdible()) {
            return;
        }
        RuntimeEffectService.handleEat(event.getEntity(), stack);
    }

    @SubscribeEvent
    public void onMobEffectApplicable(net.minecraftforge.event.entity.living.MobEffectEvent.Applicable event) {
        OrganStatService.onMobEffectApplicable(event);
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() != null) {
            OepDebug.trace(event.getPlayer(), "mine event block=%s", event.getState().getBlock().getDescriptionId());
            RuntimeEffectService.handleMine(event.getPlayer(), event.getState());
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide()) {
            RuntimeEffectService.handleUseItem(event.getEntity(), event.getItemStack());
        }
    }

    @SubscribeEvent
    public void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof LivingEntity) {
            event.addCapability(
                    EffectHolderProvider.ID,
                    new EffectHolderProvider(event.getObject())
            );
        }
    }

    private void cachePlayerState(Player player) {
        lastPositions.put(player.getUUID(), player.position());
        ResourceKey<Biome> biomeKey = getBiomeKey(player);
        if (biomeKey != null) {
            lastBiomes.put(player.getUUID(), biomeKey);
        }
        lastSprinting.put(player.getUUID(), player.isSprinting());
        lastSneaking.put(player.getUUID(), player.isCrouching());
        lastSwimming.put(player.getUUID(), player.isSwimming());
        lastOnGround.put(player.getUUID(), player.onGround());
        lastInWater.put(player.getUUID(), player.isInWater());
    }

    private ResourceKey<Biome> getBiomeKey(Player player) {
        Holder<Biome> biomeHolder = player.level().getBiome(player.blockPosition());
        return biomeHolder.unwrapKey().orElse(null);
    }
}
