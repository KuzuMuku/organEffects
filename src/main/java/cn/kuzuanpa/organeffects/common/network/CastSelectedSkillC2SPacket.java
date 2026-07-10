package cn.kuzuanpa.organeffects.common.network;

import cn.kuzuanpa.organeffects.common.skill.SkillManager;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record CastSelectedSkillC2SPacket() {
    public static void encode(CastSelectedSkillC2SPacket packet, FriendlyByteBuf buffer) {
    }

    public static CastSelectedSkillC2SPacket decode(FriendlyByteBuf buffer) {
        return new CastSelectedSkillC2SPacket();
    }

    public static void handle(CastSelectedSkillC2SPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                SkillManager.castSelectedSkill(player);
            }
        });
        context.setPacketHandled(true);
    }
}
