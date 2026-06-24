package cn.kuzuanpa.organeffectprocessor.common.network;

import cn.kuzuanpa.organeffectprocessor.OrganEffectProcessorMod;
import cn.kuzuanpa.organeffectprocessor.common.skill.SkillManager;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class OepNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(OrganEffectProcessorMod.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );
    private static int nextId;

    private OepNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(nextId++, CastSkillC2SPacket.class,
                CastSkillC2SPacket::encode,
                CastSkillC2SPacket::decode,
                CastSkillC2SPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, CastSelectedSkillC2SPacket.class,
                CastSelectedSkillC2SPacket::encode,
                CastSelectedSkillC2SPacket::decode,
                CastSelectedSkillC2SPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, SelectSkillC2SPacket.class,
                SelectSkillC2SPacket::encode,
                SelectSkillC2SPacket::decode,
                SelectSkillC2SPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(nextId++, SyncSkillsS2CPacket.class,
                SyncSkillsS2CPacket::encode,
                SyncSkillsS2CPacket::decode,
                SyncSkillsS2CPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    public static void syncSkills(ServerPlayer player) {
        Map<String, Integer> levels = SkillManager.getSkillLevels(player);
        String selectedSkillId = SkillManager.getSelectedSkillId(player);
        Map<String, Long> cooldowns = SkillManager.getCooldownRemainingTicks(player);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncSkillsS2CPacket(player.getUUID(), selectedSkillId, levels, cooldowns));
    }
}
