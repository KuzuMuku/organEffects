package cn.kuzuanpa.organeffectprocessor.common.event;

import cn.kuzuanpa.organapi.api.event.OrganStateCommittedEvent;
import cn.kuzuanpa.organeffectprocessor.common.capability.EffectHolderProvider;
import cn.kuzuanpa.organeffectprocessor.common.debug.OepDebug;
import cn.kuzuanpa.organeffectprocessor.common.effect.EffectRecalculationService;
import cn.kuzuanpa.organeffectprocessor.common.effect.RuntimeEffectService;
import cn.kuzuanpa.organeffectprocessor.common.effect.RuntimePointExecutor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ServerEventHandler {
    private static final int DYNAMIC_RECOMPUTE_INTERVAL = 20;

    private final Map<UUID, Vec3> lastPositions = new HashMap<>();

    @SubscribeEvent
    public void onOrganStateCommitted(OrganStateCommittedEvent event) {
        if (!event.wasDirty()) {
            return;
        }
        EffectRecalculationService.recompute(event.getTarget());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        EffectRecalculationService.recompute(event.getEntity());
        lastPositions.put(event.getEntity().getUUID(), event.getEntity().position());
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        EffectRecalculationService.recompute(event.getEntity());
        lastPositions.put(event.getEntity().getUUID(), event.getEntity().position());
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        EffectRecalculationService.reapply(player);
        lastPositions.put(player.getUUID(), player.position());
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        Player player = event.player;
        if (player.tickCount % DYNAMIC_RECOMPUTE_INTERVAL == 0) {
            EffectRecalculationService.recompute(player);
        } else {
            RuntimePointExecutor.execute(player);
        }
        Vec3 current = player.position();
        Vec3 previous = lastPositions.put(player.getUUID(), current);
        if (previous == null) {
            return;
        }
        double horizontalDistance = current.subtract(previous).horizontalDistance();
        if (horizontalDistance > 0.0D) {
            RuntimeEffectService.handleMove(player, horizontalDistance);
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        Entity attackerEntity = event.getSource().getEntity();
        if (attackerEntity instanceof LivingEntity attacker) {
            event.setAmount(RuntimeEffectService.handleAttack(attacker, event.getEntity(), event.getSource().getDirectEntity(), event.getAmount()));
        }
    }

    @SubscribeEvent
    public void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        ItemStack stack = event.getItem();
        if (!stack.isEdible()) {
            return;
        }
        RuntimeEffectService.handleEat(event.getEntity(), stack);
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
}
