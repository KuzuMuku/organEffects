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
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record SyncSkillsS2CPacket(UUID playerId, String selectedSkillId, Map<String, Integer> levels, Map<String, Long> cooldowns) {
    public static void encode(SyncSkillsS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.playerId());
        buffer.writeUtf(packet.selectedSkillId());
        buffer.writeVarInt(packet.levels().size());
        for (Map.Entry<String, Integer> entry : packet.levels().entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeVarInt(entry.getValue());
        }
        buffer.writeVarInt(packet.cooldowns().size());
        for (Map.Entry<String, Long> entry : packet.cooldowns().entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeVarLong(entry.getValue());
        }
    }

    public static SyncSkillsS2CPacket decode(FriendlyByteBuf buffer) {
        UUID playerId = buffer.readUUID();
        String selectedSkillId = buffer.readUtf();
        int size = buffer.readVarInt();
        Map<String, Integer> levels = new HashMap<>();
        for (int i = 0; i < size; i++) {
            levels.put(buffer.readUtf(), buffer.readVarInt());
        }
        int cooldownSize = buffer.readVarInt();
        Map<String, Long> cooldowns = new HashMap<>();
        for (int i = 0; i < cooldownSize; i++) {
            cooldowns.put(buffer.readUtf(), buffer.readVarLong());
        }
        return new SyncSkillsS2CPacket(playerId, selectedSkillId, levels, cooldowns);
    }

    public static void handle(SyncSkillsS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    long gameTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
                    Map<String, Long> cooldownExpirations = new LinkedHashMap<>();
                    for (Map.Entry<String, Long> entry : packet.cooldowns().entrySet()) {
                        if (entry.getValue() > 0L) {
                            cooldownExpirations.put(entry.getKey(), gameTime + entry.getValue());
                        }
                    }
                    SkillManager.syncClientSkillState(packet.playerId(), packet.selectedSkillId(), packet.levels(), cooldownExpirations);
                }));
        context.setPacketHandled(true);
    }
}
