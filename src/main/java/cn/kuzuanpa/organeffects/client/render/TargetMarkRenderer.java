/*
 * This class was created by <kuzuanpa>. It is distributed as
 * part of the organEffects Mod. Get the Source Code in github:
 * https://github.com/KuzuMuku/organEffects
 *
 * organEffects is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * organEffects is Open Source and distributed under the
 * AGPLv3 License: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 */

package cn.kuzuanpa.organeffects.client.render;

import cn.kuzuanpa.organeffects.OrganEffectsMod;
import cn.kuzuanpa.organeffects.common.data.PointConfigData;
import cn.kuzuanpa.organeffects.common.effect.OrganStatService;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = OrganEffectsMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TargetMarkRenderer {
    private static final double BASE_HEAD_OFFSET = 0.3D;
    private static final double MAX_DISTANCE = 64.0D;
    private static final float MARK_SPACING = 0.3F;
    private static final boolean HIDE_WHEN_GUI_HIDDEN = true;

    private TargetMarkRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (minecraft.options.hideGui && HIDE_WHEN_GUI_HIDDEN) {
            return;
        }

        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        double maxDistanceSq = MAX_DISTANCE * MAX_DISTANCE;

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        for (int entityId : OrganStatService.getClientSyncedEntityIds()) {
            Entity entity = minecraft.level.getEntity(entityId);
            if (!(entity instanceof LivingEntity living) || !entity.isAlive() || entity.isRemoved()) {
                continue;
            }
            Map<String, Long> points = OrganStatService.getClientSyncedPoints(living);
            if (points.isEmpty()) {
                continue;
            }
            double x = Mth.lerp(partialTick, entity.xo, entity.getX());
            double y = Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getBbHeight() + BASE_HEAD_OFFSET;
            double z = Mth.lerp(partialTick, entity.zo, entity.getZ());
            if (cameraPos.distanceToSqr(x, y, z) > maxDistanceSq) {
                continue;
            }
            List<Map.Entry<String, Long>> marks = points.entrySet().stream()
                    .filter(entry -> PointConfigData.isMarkPoint(entry.getKey())
                            && entry.getValue() != null
                            && entry.getValue() > 0L)
                    .sorted(Comparator
                            .comparingInt((Map.Entry<String, Long> entry) -> PointConfigData.INSTANCE
                                    .getPointConfig(entry.getKey())
                                    .map(PointConfigData.PointConfig::priority)
                                    .orElse(PointConfigData.DEFAULT_MARK_PRIORITY))
                            .thenComparing(Map.Entry::getKey))
                    .toList();
            renderMarks(poseStack, buffer, event.getCamera().rotation(), x, y, z, marks);
        }
        poseStack.popPose();
        buffer.endBatch();
    }

    private static void renderMarks(PoseStack poseStack, MultiBufferSource buffer, org.joml.Quaternionf cameraRotation,
                                    double x, double y, double z, List<Map.Entry<String, Long>> marks) {
        if (marks.isEmpty()) {
            return;
        }
        float startX = -(marks.size() - 1) * MARK_SPACING * 0.5F;
        for (int index = 0; index < marks.size(); index++) {
            Map.Entry<String, Long> entry = marks.get(index);
            PointConfigData.PointConfig config = PointConfigData.INSTANCE.getPointConfig(entry.getKey()).orElse(null);
            ResourceLocation icon = config != null ? config.markIcon() : PointConfigData.DEFAULT_MARK_ICON;
            float size = config != null ? config.markRenderScale() : PointConfigData.DEFAULT_MARK_RENDER_SCALE;
            double offset = config != null ? config.markRenderOffset() : PointConfigData.DEFAULT_MARK_RENDER_OFFSET;
            int tint = config != null ? config.markTint() : PointConfigData.DEFAULT_MARK_TINT;

            poseStack.pushPose();
            poseStack.translate(x, y + offset, z);
            poseStack.mulPose(cameraRotation);
            poseStack.translate(startX + index * MARK_SPACING, 0.0F, 0.0F);
            renderIcon(poseStack, buffer, icon, size, tint);
            poseStack.popPose();
        }
    }

    private static void renderIcon(PoseStack poseStack, MultiBufferSource buffer, ResourceLocation texture, float size, int tint) {
        if (size <= 0.0F || texture == null) {
            return;
        }
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        float half = size * 0.5F;
        int alpha = (tint >>> 24) & 0xFF;
        int red = (tint >>> 16) & 0xFF;
        int green = (tint >>> 8) & 0xFF;
        int blue = tint & 0xFF;

        vertex(consumer, poseMatrix, normalMatrix, -half, half, 0.0F, red, green, blue, alpha, 0.0F, 0.0F);
        vertex(consumer, poseMatrix, normalMatrix, -half, -half, 0.0F, red, green, blue, alpha, 0.0F, 1.0F);
        vertex(consumer, poseMatrix, normalMatrix, half, -half, 0.0F, red, green, blue, alpha, 1.0F, 1.0F);
        vertex(consumer, poseMatrix, normalMatrix, half, half, 0.0F, red, green, blue, alpha, 1.0F, 0.0F);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix,
                               float x, float y, float z, int red, int green, int blue, int alpha, float u, float v) {
        consumer.vertex(poseMatrix, x, y, z)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normalMatrix, 0.0F, 0.0F, 1.0F)
                .endVertex();
    }
}
