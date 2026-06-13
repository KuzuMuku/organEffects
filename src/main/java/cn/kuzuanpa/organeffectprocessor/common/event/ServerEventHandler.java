package cn.kuzuanpa.organeffectprocessor.common.event;

import cn.kuzuanpa.organapi.api.event.OrganStateCommittedEvent;
import cn.kuzuanpa.organeffectprocessor.common.capability.EffectHolderProvider;
import cn.kuzuanpa.organeffectprocessor.common.effect.EffectRecalculationService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ServerEventHandler {
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
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        EffectRecalculationService.recompute(event.getEntity());
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        EffectRecalculationService.reapply(player);
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
