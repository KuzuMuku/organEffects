package cn.kuzuanpa.organeffectprocessor.common.item;

import cn.kuzuanpa.organeffectprocessor.common.capability.EffectCapabilities;
import cn.kuzuanpa.organeffectprocessor.common.capability.IEffectHolder;
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
            IEffectHolder holder = player.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
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
                    entries.forEach((pointKey, value) -> player.displayClientMessage(
                            EffectPointTextHelper.toChatLine(pointKey, value, collectSourceBreakdown(holder, pointKey)), false));
                });
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static Map<String, Long> collectSourceBreakdown(IEffectHolder holder, String pointKey) {
        if (holder == null) {
            return Map.of();
        }
        Map<String, Long> breakdown = new LinkedHashMap<>();
        holder.getPointSources().forEach((source, points) -> {
            long value = points.getOrDefault(pointKey, 0L);
            if (value != 0L) {
                breakdown.put(source, value);
            }
        });
        return breakdown;
    }
}
