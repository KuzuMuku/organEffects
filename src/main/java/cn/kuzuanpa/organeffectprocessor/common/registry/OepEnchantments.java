package cn.kuzuanpa.organeffectprocessor.common.registry;

import cn.kuzuanpa.organeffectprocessor.OrganEffectProcessorMod;
import cn.kuzuanpa.organeffectprocessor.common.enchantment.TensionEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class OepEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, OrganEffectProcessorMod.MOD_ID);

    public static final RegistryObject<Enchantment> TENSION = ENCHANTMENTS.register("tension", TensionEnchantment::new);

    private OepEnchantments() {
    }
}
