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

import cn.kuzuanpa.organeffects.common.effect.OrganStatService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record SyncClientPointsS2CPacket(UUID playerId, Map<String, Long> points) {
    public static void encode(SyncClientPointsS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.playerId());
        buffer.writeVarInt(packet.points().size());
        for (Map.Entry<String, Long> entry : packet.points().entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeVarLong(entry.getValue());
        }
    }

    public static SyncClientPointsS2CPacket decode(FriendlyByteBuf buffer) {
        UUID playerId = buffer.readUUID();
        int size = buffer.readVarInt();
        Map<String, Long> points = new HashMap<>();
        for (int i = 0; i < size; i++) {
            points.put(buffer.readUtf(), buffer.readVarLong());
        }
        return new SyncClientPointsS2CPacket(playerId, points);
    }

    public static void handle(SyncClientPointsS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> OrganStatService.syncClientPoints(packet.playerId(), packet.points())));
        context.setPacketHandled(true);
    }
}
