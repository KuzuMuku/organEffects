package cn.kuzuanpa.organeffects.common.event;

import cn.kuzuanpa.organapi.api.organ.OrganDefinition;
import cn.kuzuanpa.organapi.common.data.OrganRegistryAccess;
import cn.kuzuanpa.organeffects.common.data.OrganEffectData;
import cn.kuzuanpa.organeffects.common.point.OrganEffectDisplayBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ClientTooltipHandler {
    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        OrganDefinition definition = OrganRegistryAccess.getOrgan(event.getItemStack()).orElse(null);
        if (definition == null) {
            return;
        }
        event.getToolTip().addAll(OrganEffectDisplayBuilder.buildTooltipLines(
                definition,
                OrganEffectData.INSTANCE.getEffectsForOrgan(definition.id()),
                Screen.hasShiftDown()
        ));
    }
}
