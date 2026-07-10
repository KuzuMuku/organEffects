package cn.kuzuanpa.organeffectprocessor.client.render;

import cn.kuzuanpa.organeffectprocessor.OrganEffectProcessorMod;
import cn.kuzuanpa.organeffectprocessor.common.network.BeamEffectS2CPacket;
import cn.kuzuanpa.organeffectprocessor.common.network.OepNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = OrganEffectProcessorMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BeamEffectRenderer {
    private static final ResourceLocation GUARDIAN_BEAM_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/guardian_beam.png");
    private static final RenderType GUARDIAN_BEAM_RENDER_TYPE = RenderType.entityCutoutNoCull(GUARDIAN_BEAM_TEXTURE);
    private static final Map<BeamKey, BeamState> ACTIVE_BEAMS = new HashMap<>();

    private BeamEffectRenderer() {
    }

    public static void handlePacket(BeamEffectS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        BeamKey key = new BeamKey(packet.kind(), packet.sourceEntityId());
        if (packet.durationTicks() <= 0 || packet.targetEntityId() < 0) {
            ACTIVE_BEAMS.remove(key);
            return;
        }
        long now = minecraft.level.getGameTime();
        ACTIVE_BEAMS.put(key, new BeamState(packet.kind(), packet.sourceEntityId(), packet.targetEntityId(), now, now + packet.durationTicks()));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ACTIVE_BEAMS.clear();
            return;
        }
        long now = minecraft.level.getGameTime();
        Iterator<Map.Entry<BeamKey, BeamState>> iterator = ACTIVE_BEAMS.entrySet().iterator();
        while (iterator.hasNext()) {
            BeamState state = iterator.next().getValue();
            if (state.expiresAtTick() <= now
                    || minecraft.level.getEntity(state.sourceEntityId()) == null
                    || minecraft.level.getEntity(state.targetEntityId()) == null) {
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE_BEAMS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        for (BeamState state : ACTIVE_BEAMS.values()) {
            Entity source = minecraft.level.getEntity(state.sourceEntityId());
            Entity target = minecraft.level.getEntity(state.targetEntityId());
            if (source == null || target == null) {
                continue;
            }
            if (state.kind() == OepNetwork.BeamEffectKind.END_CRYSTAL && source instanceof EndCrystal crystal) {
                renderEndCrystalBeam(crystal, target, partialTick, poseStack, buffer);
            } else if (state.kind() == OepNetwork.BeamEffectKind.GUARDIAN) {
                renderGuardianBeam(source, target, state, partialTick, poseStack, buffer);
            }
        }
        poseStack.popPose();
        buffer.endBatch();
    }

    private static void renderEndCrystalBeam(EndCrystal crystal, Entity target, float partialTick, PoseStack poseStack, MultiBufferSource buffer) {
        double targetX = Mth.lerp(partialTick, target.xo, target.getX());
        double targetY = Mth.lerp(partialTick, target.yo, target.getY()) + target.getBbHeight() * 0.5D;
        double targetZ = Mth.lerp(partialTick, target.zo, target.getZ());
        double sourceX = Mth.lerp(partialTick, crystal.xo, crystal.getX());
        double sourceY = Mth.lerp(partialTick, crystal.yo, crystal.getY());
        double sourceZ = Mth.lerp(partialTick, crystal.zo, crystal.getZ());
        float bob = EndCrystalRenderer.getY(crystal, partialTick);

        poseStack.pushPose();
        poseStack.translate(targetX, targetY, targetZ);
        EnderDragonRenderer.renderCrystalBeams(
                (float) (sourceX - targetX),
                (float) (sourceY - targetY) + bob,
                (float) (sourceZ - targetZ),
                partialTick,
                crystal.time,
                poseStack,
                buffer,
                LightTexture.FULL_BRIGHT
        );
        poseStack.popPose();
    }

    private static void renderGuardianBeam(Entity source, Entity target, BeamState state, float partialTick, PoseStack poseStack, MultiBufferSource buffer) {
        Vec3 sourcePos = getGuardianBeamSource(source, partialTick);
        Vec3 targetPos = getTargetPosition(target, partialTick);
        Vec3 delta = targetPos.subtract(sourcePos);
        float length = (float) delta.length() + 1.0F;
        if (length <= 0.001F) {
            return;
        }

        Vec3 direction = delta.normalize();
        float pitch = (float) Math.acos(direction.y);
        float yaw = (float) Math.atan2(direction.z, direction.x);
        float elapsed = (Minecraft.getInstance().level.getGameTime() - state.startedAtTick()) + partialTick;
        float progress = Mth.clamp(elapsed / Math.max(1.0F, state.expiresAtTick() - state.startedAtTick()), 0.0F, 1.0F);
        float animation = elapsed * 0.05F * -1.5F;
        float animationOffset = elapsed * 0.5F % 1.0F;
        float animationStart = -1.0F + animationOffset;
        float animationEnd = length * 2.5F + animationStart;
        float intensity = progress * progress;
        int red = 64 + (int) (intensity * 191.0F);
        int green = 32 + (int) (intensity * 191.0F);
        int blue = 128 - (int) (intensity * 64.0F);

        float radiusOuter = 0.282F;
        float radiusInner = 0.2F;

        float outer1x = Mth.cos(animation + 2.3561945F) * radiusOuter;
        float outer1z = Mth.sin(animation + 2.3561945F) * radiusOuter;
        float outer2x = Mth.cos(animation + 0.7853982F) * radiusOuter;
        float outer2z = Mth.sin(animation + 0.7853982F) * radiusOuter;
        float outer3x = Mth.cos(animation + 3.926991F) * radiusOuter;
        float outer3z = Mth.sin(animation + 3.926991F) * radiusOuter;
        float outer4x = Mth.cos(animation + 5.4977875F) * radiusOuter;
        float outer4z = Mth.sin(animation + 5.4977875F) * radiusOuter;

        float inner1x = Mth.cos(animation + (float) Math.PI) * radiusInner;
        float inner1z = Mth.sin(animation + (float) Math.PI) * radiusInner;
        float inner2x = Mth.cos(animation) * radiusInner;
        float inner2z = Mth.sin(animation) * radiusInner;
        float inner3x = Mth.cos(animation + 1.5707964F) * radiusInner;
        float inner3z = Mth.sin(animation + 1.5707964F) * radiusInner;
        float inner4x = Mth.cos(animation + 4.712389F) * radiusInner;
        float inner4z = Mth.sin(animation + 4.712389F) * radiusInner;

        poseStack.pushPose();
        poseStack.translate(sourcePos.x, sourcePos.y, sourcePos.z);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees((90.0F - yaw * 57.295776F)));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch * 57.295776F));

        VertexConsumer consumer = buffer.getBuffer(GUARDIAN_BEAM_RENDER_TYPE);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();

        vertex(consumer, poseMatrix, normalMatrix, inner1x, length, inner1z, red, green, blue, 0.4999F, animationEnd);
        vertex(consumer, poseMatrix, normalMatrix, inner1x, 0.0F, inner1z, red, green, blue, 0.4999F, animationStart);
        vertex(consumer, poseMatrix, normalMatrix, inner2x, 0.0F, inner2z, red, green, blue, 0.0F, animationStart);
        vertex(consumer, poseMatrix, normalMatrix, inner2x, length, inner2z, red, green, blue, 0.0F, animationEnd);
        vertex(consumer, poseMatrix, normalMatrix, inner3x, length, inner3z, red, green, blue, 0.4999F, animationEnd);
        vertex(consumer, poseMatrix, normalMatrix, inner3x, 0.0F, inner3z, red, green, blue, 0.4999F, animationStart);
        vertex(consumer, poseMatrix, normalMatrix, inner4x, 0.0F, inner4z, red, green, blue, 0.0F, animationStart);
        vertex(consumer, poseMatrix, normalMatrix, inner4x, length, inner4z, red, green, blue, 0.0F, animationEnd);

        float flickerOffset = source.tickCount % 2 == 0 ? 0.5F : 0.0F;
        vertex(consumer, poseMatrix, normalMatrix, outer1x, length, outer1z, red, green, blue, 0.5F, flickerOffset + 0.5F);
        vertex(consumer, poseMatrix, normalMatrix, outer2x, length, outer2z, red, green, blue, 1.0F, flickerOffset + 0.5F);
        vertex(consumer, poseMatrix, normalMatrix, outer4x, length, outer4z, red, green, blue, 1.0F, flickerOffset);
        vertex(consumer, poseMatrix, normalMatrix, outer3x, length, outer3z, red, green, blue, 0.5F, flickerOffset);
        poseStack.popPose();
    }

    private static Vec3 getGuardianBeamSource(Entity source, float partialTick) {
        if (source instanceof LivingEntity living) {
            Vec3 eye = living.getEyePosition(partialTick);
            Vec3 look = living.getLookAngle().normalize();
            return eye.add(look.scale(0.4D)).add(0.0D, -0.5D, 0.0D);
        }
        return new Vec3(Mth.lerp(partialTick, source.xo, source.getX()),
                Mth.lerp(partialTick, source.yo, source.getY()) + source.getBbHeight() * 0.5D,
                Mth.lerp(partialTick, source.zo, source.getZ()));
    }

    private static Vec3 getTargetPosition(Entity target, float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, target.xo, target.getX()),
                Mth.lerp(partialTick, target.yo, target.getY()) + target.getBbHeight() * 0.5D,
                Mth.lerp(partialTick, target.zo, target.getZ())
        );
    }

    private static void vertex(VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix,
                               float x, float y, float z, int red, int green, int blue, float u, float v) {
        consumer.vertex(poseMatrix, x, y, z)
                .color(red, green, blue, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normalMatrix, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    private record BeamKey(OepNetwork.BeamEffectKind kind, int sourceEntityId) {
    }

    private record BeamState(OepNetwork.BeamEffectKind kind, int sourceEntityId, int targetEntityId, long startedAtTick, long expiresAtTick) {
    }
}
