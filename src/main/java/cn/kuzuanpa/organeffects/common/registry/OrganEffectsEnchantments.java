package cn.kuzuanpa.organeffects.common.registry;

import cn.kuzuanpa.organeffects.OrganEffectProcessorMod;
import cn.kuzuanpa.organeffects.common.enchantment.TensionEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class OrganEffectsEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, OrganEffectProcessorMod.MOD_ID);

    public static final RegistryObject<Enchantment> TENSION = ENCHANTMENTS.register("tension", TensionEnchantment::new);

    private OrganEffectsEnchantments() {
    }
}
