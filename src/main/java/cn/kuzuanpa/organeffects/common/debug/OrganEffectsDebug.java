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

package cn.kuzuanpa.organeffects.common.debug;

import cn.kuzuanpa.organeffects.common.capability.EffectCapabilities;
import cn.kuzuanpa.organeffects.common.capability.IEffectHolder;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

public final class OrganEffectsDebug {
    private static final Logger LOGGER = LogUtils.getLogger();

    private OrganEffectsDebug() {
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
                ? "message.organeffects.debug.enabled"
                : "message.organeffects.debug.disabled").withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return enabled;
    }

    public static void trace(Player player, String message, Object... args) {
        if (!isEnabled(player)) {
            return;
        }
        String formatted = args.length == 0 ? message : String.format(message, args);
        player.displayClientMessage(Component.literal("[OrganEffectsDBG] " + formatted).withStyle(ChatFormatting.DARK_AQUA), false);
        LOGGER.info("[OrganEffectsDBG][{}] {}", player.getScoreboardName(), formatted);
    }
}
