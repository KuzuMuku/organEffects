package cn.kuzuanpa.organeffectprocessor.client.render;

import cn.kuzuanpa.organeffectprocessor.common.effect.OrganStatService;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(value = net.minecraftforge.api.distmarker.Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BowItemPropertyRegistrar {
    private static final ResourceLocation PULL = ResourceLocation.withDefaultNamespace("pull");
    private static final ResourceLocation PULLING = ResourceLocation.withDefaultNamespace("pulling");
    private static final Field USE_ITEM_REMAINING_FIELD = ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20936_");

    private BowItemPropertyRegistrar() {
    }

    public static void registerAllBowProperties() {
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (!(item instanceof BowItem bowItem)) {
                continue;
            }
            ItemProperties.register(bowItem, PULL, BowItemPropertyRegistrar::getPullProgress);
            ItemProperties.register(bowItem, PULLING, (stack, level, entity, seed) -> isPulling(entity, stack) ? 1.0F : 0.0F);
        }
    }

    private static float getPullProgress(ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level, LivingEntity entity, int seed) {
        if (entity == null) {
            return 0.0F;
        }
        return OrganStatService.getAdjustedBowPullProgress(entity, stack);
    }

    private static boolean isPulling(LivingEntity entity, ItemStack stack) {
        return entity != null && entity.isUsingItem() && entity.getUseItem() == stack;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        LivingEntity player = minecraft.player;
        ItemStack useItem = player.getUseItem();
        if (!(useItem.getItem() instanceof BowItem) || !player.isUsingItem()) {
            return;
        }

        long strength = OrganStatService.getPositiveMuscularStrength(player);
        if (strength <= 0L) {
            return;
        }

        int useDuration = useItem.getUseDuration();
        int currentRemaining = player.getUseItemRemainingTicks();
        int rawCharge = useDuration - currentRemaining;
        int adjustedCharge = Math.min(useDuration, rawCharge + Math.toIntExact(strength * 2L));
        int adjustedRemaining = Math.max(0, useDuration - adjustedCharge);

        if (adjustedRemaining >= currentRemaining) {
            return;
        }

        try {
            USE_ITEM_REMAINING_FIELD.setInt(player, adjustedRemaining);
        } catch (IllegalAccessException exception) {
            throw new RuntimeException("Failed to adjust client bow useItemRemaining", exception);
        }
    }
}
