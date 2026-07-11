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

package cn.kuzuanpa.organeffects.common.network;

import cn.kuzuanpa.organeffects.common.skill.SkillManager;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record SelectSkillC2SPacket(String skillId) {
    public static void encode(SelectSkillC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.skillId());
    }

    public static SelectSkillC2SPacket decode(FriendlyByteBuf buffer) {
        return new SelectSkillC2SPacket(buffer.readUtf());
    }

    public static void handle(SelectSkillC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                SkillManager.selectSkill(player, packet.skillId());
            }
        });
        context.setPacketHandled(true);
    }
}
