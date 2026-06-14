package cn.kuzuanpa.organeffectprocessor.common.item;

import cn.kuzuanpa.organapi.api.organ.OrganDefinition;
import cn.kuzuanpa.organapi.common.data.OrganRegistryAccess;
import cn.kuzuanpa.organapi.common.item.OrganItem;
import cn.kuzuanpa.organeffectprocessor.common.data.OrganEffectData;
import cn.kuzuanpa.organeffectprocessor.common.point.OrganEffectDisplayBuilder;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Organ item for Organ Effect Processor. Delegates to the base {@link OrganItem}
 * and references the organ definition under {@code organapi/organs/<name>.json}.
 */
public class OepOrganItem extends OrganItem {
    public OepOrganItem(Properties properties, String definitionName) {
        super(properties, ResourceLocation.fromNamespaceAndPath("organeffectprocessor", definitionName));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        OrganDefinition definition = OrganRegistryAccess.getOrgan(getDefinitionId(stack)).orElse(null);
        if (definition == null) {
            super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
            return;
        }
        tooltipComponents.addAll(OrganEffectDisplayBuilder.buildTooltipLines(
                definition,
                OrganEffectData.INSTANCE.getEffectsForOrgan(definition.id()),
                Screen.hasShiftDown()
        ));
    }
}
