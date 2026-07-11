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

import cn.kuzuanpa.organeffects.client.render.BeamEffectRenderer;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record BeamEffectS2CPacket(OrganEffectsNetwork.BeamEffectKind kind, int sourceEntityId, int targetEntityId, int durationTicks) {
    public static void encode(BeamEffectS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.kind());
        buffer.writeVarInt(packet.sourceEntityId());
        buffer.writeVarInt(packet.targetEntityId());
        buffer.writeVarInt(packet.durationTicks());
    }

    public static BeamEffectS2CPacket decode(FriendlyByteBuf buffer) {
        return new BeamEffectS2CPacket(
                buffer.readEnum(OrganEffectsNetwork.BeamEffectKind.class),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    public static void handle(BeamEffectS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> BeamEffectRenderer.handlePacket(packet)));
        context.setPacketHandled(true);
    }
}
