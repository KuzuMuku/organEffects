package cn.kuzuanpa.organeffectprocessor.common.registry;

import cn.kuzuanpa.organeffectprocessor.OrganEffectProcessorMod;
import cn.kuzuanpa.organeffectprocessor.common.item.EffectPointViewerItem;
import cn.kuzuanpa.organeffectprocessor.common.item.OepOrganItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class OepItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, OrganEffectProcessorMod.MOD_ID);

    public static final RegistryObject<Item> EFFECT_POINT_VIEWER = ITEMS.register("effect_point_viewer",
            () -> new EffectPointViewerItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WONDER_BRAIN = ITEMS.register("wonder_brain",
            () -> new OepOrganItem(new Item.Properties().stacksTo(1), "wonder_brain"));
    public static final RegistryObject<Item> WONDER_BRAIN_V2 = ITEMS.register("wonder_brain_v2",
            () -> new OepOrganItem(new Item.Properties().stacksTo(1), "wonder_brain_v2"));
    public static final RegistryObject<Item> WONDER_HEART = ITEMS.register("wonder_heart",
            () -> new OepOrganItem(new Item.Properties().stacksTo(1), "wonder_heart"));
    public static final RegistryObject<Item> WONDER_LUNG = ITEMS.register("wonder_lung",
            () -> new OepOrganItem(new Item.Properties().stacksTo(1), "wonder_lung"));
    public static final RegistryObject<Item> WONDER_LEG_MUSCLE = ITEMS.register("wonder_leg_muscle",
            () -> new OepOrganItem(new Item.Properties().stacksTo(1), "wonder_leg_muscle"));
    public static final RegistryObject<Item> WONDER_TENDON = ITEMS.register("wonder_tendon",
            () -> new OepOrganItem(new Item.Properties().stacksTo(1), "wonder_tendon"));
    public static final RegistryObject<Item> WONDER_EYE_OF_STORM = ITEMS.register("wonder_eye_of_storm",
            () -> new OepOrganItem(new Item.Properties().stacksTo(1), "wonder_eye_of_storm"));

    private OepItems() {}
}
