package cn.kuzuanpa.organeffectprocessor.common.network;

import cn.kuzuanpa.organeffectprocessor.common.skill.SkillManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record SyncSkillsS2CPacket(UUID playerId, String selectedSkillId, Map<String, Integer> levels) {
    public static void encode(SyncSkillsS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.playerId());
        buffer.writeUtf(packet.selectedSkillId());
        buffer.writeVarInt(packet.levels().size());
        for (Map.Entry<String, Integer> entry : packet.levels().entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeVarInt(entry.getValue());
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
        return new SyncSkillsS2CPacket(playerId, selectedSkillId, levels);
    }

    public static void handle(SyncSkillsS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> SkillManager.syncClientSkillState(packet.playerId(), packet.selectedSkillId(), packet.levels())));
        context.setPacketHandled(true);
    }
}
