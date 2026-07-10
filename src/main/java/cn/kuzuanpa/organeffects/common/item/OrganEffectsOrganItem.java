package cn.kuzuanpa.organeffects.common.item;

import cn.kuzuanpa.organapi.common.item.OrganItem;
import net.minecraft.resources.ResourceLocation;

/**
 * Organ item for Organ Effect Processor. Delegates to the base {@link OrganItem}
 * and references the organ definition under {@code organapi/organs/<name>.json}.
 */
public class OrganEffectsOrganItem extends OrganItem {
    public OrganEffectsOrganItem(Properties properties, String definitionName) {
        super(properties, ResourceLocation.fromNamespaceAndPath("organeffects", definitionName));
    }
}
