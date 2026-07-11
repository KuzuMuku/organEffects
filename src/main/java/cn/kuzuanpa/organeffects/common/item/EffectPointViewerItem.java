/*
 * This class was created by <kuzuanpa>. It is distributed as
 * part of the organEffects Mod. Get the Source Code in github:
 * https://github.com/KuzuMuku/organEffects
 *
 * organEffects is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.

 * organEffects is Open Source and distributed under the
 * AGPLv3 License: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 */

package cn.kuzuanpa.organeffects.common.item;

import cn.kuzuanpa.organapi.api.query.OrganQueryService;
import cn.kuzuanpa.organeffects.common.capability.EffectCapabilities;
import cn.kuzuanpa.organeffects.common.capability.IEffectHolder;
import cn.kuzuanpa.organeffects.common.debug.OrganEffectsDebug;
import cn.kuzuanpa.organeffects.common.effect.EffectRecalculationService;
import cn.kuzuanpa.organeffects.common.point.EffectPointTextHelper;
import cn.kuzuanpa.organeffects.common.point.OrganEffectDisplayBuilder;
import java.util.LinkedHashMap;
import java.util.List;
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
            if (player.isShiftKeyDown()) {
                OrganEffectsDebug.toggle(player);
                return InteractionResultHolder.sidedSuccess(stack, false);
            }
            Map<String, Long> points = EffectRecalculationService.recompute(player);
            IEffectHolder holder = player.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
            if (points.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.organeffects.points.empty").withStyle(ChatFormatting.YELLOW), false);
            } else {
                player.displayClientMessage(Component.translatable("message.organeffects.points.header").withStyle(ChatFormatting.AQUA), false);
                if (holder != null) {
                    displayGroup(player, "static", holder.getStaticPoints(), holder);
                    displayGroup(player, "runtime", holder.getRuntimePoints(), holder);
                } else {
                    displayGroup(player, "all", points, null);
                }
            }
            displayPotentialEffects(player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void displayPotentialEffects(Player player) {
        List<Component> lines = OrganEffectDisplayBuilder.buildViewerEffectLines(player, OrganQueryService.getInstalledOrganPositions(player));
        if (lines.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.organeffects.effects.viewer_empty").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        player.displayClientMessage(Component.translatable("message.organeffects.effects.viewer_header").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        for (Component line : lines) {
            player.displayClientMessage(line, false);
        }
    }

    private static void displayGroup(Player player, String label, Map<String, Long> points, IEffectHolder holder) {
        if (points.isEmpty()) {
            return;
        }
        player.displayClientMessage(Component.literal("[" + label + "]").withStyle(ChatFormatting.AQUA), false);
        Map<String, Map<String, Long>> grouped = points.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.groupingBy(entry -> EffectPointTextHelper.getPointType(entry.getKey()), LinkedHashMap::new,
                        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> right, LinkedHashMap::new)));
        grouped.forEach((group, entries) -> {
            player.displayClientMessage(EffectPointTextHelper.getGroupHeader(group), false);
            entries.forEach((pointKey, value) -> player.displayClientMessage(
                    EffectPointTextHelper.toChatLine(pointKey, value, collectSourceBreakdown(holder, label, pointKey)), false));
        });
    }

    private static Map<String, Long> collectSourceBreakdown(IEffectHolder holder, String label, String pointKey) {
        if (holder == null) {
            return Map.of();
        }
        Map<String, Long> breakdown = new LinkedHashMap<>();
        if ("runtime".equals(label)) {
            long value = holder.getRuntimePoints().getOrDefault(pointKey, 0L);
            if (value != 0L) {
                long expireAt = holder.getRuntimeExpirations().getOrDefault(pointKey, 0L);
                breakdown.put(expireAt > 0L ? "runtime (expires @ " + expireAt + ")" : "runtime", value);
            }
            return breakdown;
        }
        holder.getPointSources().forEach((source, points) -> {
            long value = points.getOrDefault(pointKey, 0L);
            if (value != 0L && !"runtime".equals(source)) {
                breakdown.put(source, value);
            }
        });
        return breakdown;
    }

}
