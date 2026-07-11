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

import cn.kuzuanpa.organeffects.OrganEffectsMod;
import cn.kuzuanpa.organeffects.common.skill.SkillManager;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class OrganEffectsNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(OrganEffectsMod.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );
    private static int nextId;

    private OrganEffectsNetwork() {
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
        CHANNEL.registerMessage(nextId++, SyncClientPointsS2CPacket.class,
                SyncClientPointsS2CPacket::encode,
                SyncClientPointsS2CPacket::decode,
                SyncClientPointsS2CPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, BeamEffectS2CPacket.class,
                BeamEffectS2CPacket::encode,
                BeamEffectS2CPacket::decode,
                BeamEffectS2CPacket::handle,
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

    public static void syncClientPoints(ServerPlayer player, Map<String, Long> points) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncClientPointsS2CPacket(player.getUUID(), points));
    }

    public static void sendBeamEffect(Entity source, BeamEffectKind kind, Entity target, int durationTicks) {
        if (source.level().isClientSide()) {
            return;
        }
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> source),
                new BeamEffectS2CPacket(kind, source.getId(), target.getId(), durationTicks));
    }

    public static void clearBeamEffect(Entity source, BeamEffectKind kind) {
        if (source.level().isClientSide()) {
            return;
        }
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> source),
                new BeamEffectS2CPacket(kind, source.getId(), -1, 0));
    }

    public enum BeamEffectKind {
        GUARDIAN,
        END_CRYSTAL
    }
}
