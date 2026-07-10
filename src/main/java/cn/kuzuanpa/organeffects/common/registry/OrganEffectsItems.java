package cn.kuzuanpa.organeffects.common.registry;

import cn.kuzuanpa.organeffects.OrganEffectProcessorMod;
import cn.kuzuanpa.organeffects.common.item.EffectPointViewerItem;
import cn.kuzuanpa.organeffects.common.item.OrganEffectsOrganItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class OrganEffectsItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, OrganEffectProcessorMod.MOD_ID);

    public static final RegistryObject<Item> EFFECT_POINT_VIEWER = ITEMS.register("effect_point_viewer",
            () -> new EffectPointViewerItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WONDER_BRAIN = ITEMS.register("wonder_brain",
            () -> new OrganEffectsOrganItem(new Item.Properties().stacksTo(1), "wonder_brain"));
    public static final RegistryObject<Item> WONDER_BRAIN_V2 = ITEMS.register("wonder_brain_v2",
            () -> new OrganEffectsOrganItem(new Item.Properties().stacksTo(1), "wonder_brain_v2"));
    public static final RegistryObject<Item> WONDER_HEART = ITEMS.register("wonder_heart",
            () -> new OrganEffectsOrganItem(new Item.Properties().stacksTo(1), "wonder_heart"));
    public static final RegistryObject<Item> WONDER_LUNG = ITEMS.register("wonder_lung",
            () -> new OrganEffectsOrganItem(new Item.Properties().stacksTo(1), "wonder_lung"));
    public static final RegistryObject<Item> WONDER_LEG_MUSCLE = ITEMS.register("wonder_leg_muscle",
            () -> new OrganEffectsOrganItem(new Item.Properties().stacksTo(1), "wonder_leg_muscle"));
    public static final RegistryObject<Item> WONDER_TENDON = ITEMS.register("wonder_tendon",
            () -> new OrganEffectsOrganItem(new Item.Properties().stacksTo(1), "wonder_tendon"));
    public static final RegistryObject<Item> WONDER_EYE_OF_STORM = ITEMS.register("wonder_eye_of_storm",
            () -> new OrganEffectsOrganItem(new Item.Properties().stacksTo(1), "wonder_eye_of_storm"));
    public static final RegistryObject<Item> WONDER_BIOME_CORE = ITEMS.register("wonder_biome_core",
            () -> new OrganEffectsOrganItem(new Item.Properties().stacksTo(1), "wonder_biome_core"));
    public static final RegistryObject<Item> WONDER_DIMENSION_CORE = ITEMS.register("wonder_dimension_core",
            () -> new OrganEffectsOrganItem(new Item.Properties().stacksTo(1), "wonder_dimension_core"));
    public static final RegistryObject<Item> WONDER_LIGHT_CORE = ITEMS.register("wonder_light_core",
            () -> new OrganEffectsOrganItem(new Item.Properties().stacksTo(1), "wonder_light_core"));
    public static final RegistryObject<Item> WONDER_FOOTING_CORE = ITEMS.register("wonder_footing_core",
            () -> new OrganEffectsOrganItem(new Item.Properties().stacksTo(1), "wonder_footing_core"));
    public static final RegistryObject<Item> WONDER_GUARD_CORE = ITEMS.register("wonder_guard_core",
            () -> new OrganEffectsOrganItem(new Item.Properties().stacksTo(1), "wonder_guard_core"));
    public static final RegistryObject<Item> WONDER_HUNTER_CORE = ITEMS.register("wonder_hunter_core",
            () -> new OrganEffectsOrganItem(new Item.Properties().stacksTo(1), "wonder_hunter_core"));
    public static final RegistryObject<Item> WONDER_DRIFTER_CORE = ITEMS.register("wonder_drifter_core",
            () -> new OrganEffectsOrganItem(new Item.Properties().stacksTo(1), "wonder_drifter_core"));
    public static final RegistryObject<Item> WONDER_WARP_CORE = ITEMS.register("wonder_warp_core",
            () -> new OrganEffectsOrganItem(new Item.Properties().stacksTo(1), "wonder_warp_core"));
    public static final RegistryObject<Item> WONDER_TAUNT_CORE = ITEMS.register("wonder_taunt_core",
            () -> new OrganEffectsOrganItem(new Item.Properties().stacksTo(1), "wonder_taunt_core"));

    private OrganEffectsItems() {}
}
