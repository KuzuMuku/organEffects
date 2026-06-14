package cn.kuzuanpa.organeffectprocessor.common.debug;

import cn.kuzuanpa.organeffectprocessor.common.capability.EffectCapabilities;
import cn.kuzuanpa.organeffectprocessor.common.capability.IEffectHolder;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

public final class OepDebug {
    private static final Logger LOGGER = LogUtils.getLogger();

    private OepDebug() {
    }

    public static boolean isEnabled(Player player) {
        IEffectHolder holder = player.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        return holder != null && holder.isDebugEnabled();
    }

    public static boolean toggle(Player player) {
        IEffectHolder holder = player.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder == null) {
            return false;
        }
        boolean enabled = !holder.isDebugEnabled();
        holder.setDebugEnabled(enabled);
        player.displayClientMessage(Component.translatable(enabled
                ? "message.organeffectprocessor.debug.enabled"
                : "message.organeffectprocessor.debug.disabled").withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return enabled;
    }

    public static void trace(Player player, String message, Object... args) {
        if (!isEnabled(player)) {
            return;
        }
        String formatted = args.length == 0 ? message : String.format(message, args);
        player.displayClientMessage(Component.literal("[OEPDBG] " + formatted).withStyle(ChatFormatting.DARK_AQUA), false);
        LOGGER.info("[OEPDBG][{}] {}", player.getScoreboardName(), formatted);
    }
}
