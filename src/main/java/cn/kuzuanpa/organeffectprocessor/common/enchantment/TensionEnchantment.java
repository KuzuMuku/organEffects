package cn.kuzuanpa.organeffectprocessor.common.enchantment;

import cn.kuzuanpa.organeffectprocessor.common.config.OepServerConfig;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class TensionEnchantment extends Enchantment {
    public TensionEnchantment() {
        super(Rarity.UNCOMMON, EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int level) {
        return 12 + (level - 1) * 10;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 20;
    }

    @Override
    public int getMaxLevel() {
        return OepServerConfig.getVelocityCapEnchantmentMaxLevel();
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof BowItem || super.canEnchant(stack);
    }
}
