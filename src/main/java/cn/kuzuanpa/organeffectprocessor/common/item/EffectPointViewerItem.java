package cn.kuzuanpa.organeffectprocessor.common.item;

import cn.kuzuanpa.organeffectprocessor.common.effect.EffectRecalculationService;
import cn.kuzuanpa.organeffectprocessor.common.point.EffectPointTextHelper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class EffectPointViewerItem extends Item {
    public EffectPointViewerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            Map<String, Long> points = EffectRecalculationService.recompute(player);
            if (points.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.organeffectprocessor.points.empty").withStyle(ChatFormatting.YELLOW), false);
            } else {
                player.displayClientMessage(Component.translatable("message.organeffectprocessor.points.header").withStyle(ChatFormatting.AQUA), false);
                Map<String, Map<String, Long>> grouped = points.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .collect(Collectors.groupingBy(entry -> EffectPointTextHelper.getPointType(entry.getKey()), LinkedHashMap::new,
                                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> right, LinkedHashMap::new)));
                grouped.forEach((group, entries) -> {
                    player.displayClientMessage(EffectPointTextHelper.getGroupHeader(group), false);
                    entries.forEach((pointKey, value) -> player.displayClientMessage(EffectPointTextHelper.toChatLine(pointKey, value), false));
                });
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
