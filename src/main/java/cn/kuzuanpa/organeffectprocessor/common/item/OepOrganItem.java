package cn.kuzuanpa.organeffectprocessor.common.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import cn.kuzuanpa.organapi.common.item.OrganItem;

/**
 * Organ item for Organ Effect Processor. Delegates to the base {@link OrganItem}
 * and references the organ definition under {@code organapi/organs/<name>.json}.
 */
public class OepOrganItem extends OrganItem {
    public OepOrganItem(Properties properties, String definitionName) {
        super(properties, ResourceLocation.fromNamespaceAndPath("organeffectprocessor", definitionName));
    }
}
