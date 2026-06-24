package cn.kuzuanpa.organeffectprocessor.common.network;

import cn.kuzuanpa.organeffectprocessor.common.skill.SkillManager;
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
