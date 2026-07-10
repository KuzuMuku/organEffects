package cn.kuzuanpa.organeffectprocessor.common.network;

import cn.kuzuanpa.organeffectprocessor.client.render.BeamEffectRenderer;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record BeamEffectS2CPacket(OepNetwork.BeamEffectKind kind, int sourceEntityId, int targetEntityId, int durationTicks) {
    public static void encode(BeamEffectS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.kind());
        buffer.writeVarInt(packet.sourceEntityId());
        buffer.writeVarInt(packet.targetEntityId());
        buffer.writeVarInt(packet.durationTicks());
    }

    public static BeamEffectS2CPacket decode(FriendlyByteBuf buffer) {
        return new BeamEffectS2CPacket(
                buffer.readEnum(OepNetwork.BeamEffectKind.class),
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
