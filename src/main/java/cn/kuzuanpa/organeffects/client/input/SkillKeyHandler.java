package cn.kuzuanpa.organeffects.client.input;

import com.mojang.blaze3d.systems.RenderSystem;
import cn.kuzuanpa.organeffects.client.screen.SkillWheelScreen;
import cn.kuzuanpa.organeffects.common.network.CastSelectedSkillC2SPacket;
import cn.kuzuanpa.organeffects.common.network.OrganEffectsNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = cn.kuzuanpa.organeffects.OrganEffectProcessorMod.MOD_ID)
public final class SkillKeyHandler {
    private static final int LONG_PRESS_TICKS = 10;
    private static boolean wasDown;
    private static int heldTicks;
    private static SkillWheelScreen activeWheel;

    private SkillKeyHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            reset();
            return;
        }

        boolean isDown = OrganEffectsKeyMappings.SKILL_KEY.isDown();
        if (isDown) {
            heldTicks++;
            if (activeWheel == null && heldTicks >= LONG_PRESS_TICKS) {
                SkillWheelScreen wheel = new SkillWheelScreen(player);
                if (!wheel.isEmpty()) {
                    activeWheel = wheel;
                }
            }
        }

        if (!isDown && wasDown) {
            if (activeWheel != null) {
                activeWheel.applySelection();
            } else if (heldTicks > 0 && heldTicks < LONG_PRESS_TICKS) {
                OrganEffectsNetwork.sendToServer(new CastSelectedSkillC2SPacket());
            }
            reset();
        }
        wasDown = isDown;
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiEvent.Post event) {
        SkillWheelScreen wheel = activeWheel;
        Minecraft minecraft = Minecraft.getInstance();
        if (wheel == null || minecraft.player == null) {
            return;
        }
        MouseHandler mouseHandler = minecraft.mouseHandler;
        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        double scaleX = (double) guiWidth / minecraft.getWindow().getScreenWidth();
        double scaleY = (double) guiHeight / minecraft.getWindow().getScreenHeight();
        int mouseX = (int) Math.round(mouseHandler.xpos() * scaleX);
        int mouseY = (int) Math.round(mouseHandler.ypos() * scaleY);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        wheel.render(event.getGuiGraphics(), guiWidth, guiHeight, mouseX, mouseY);
        event.getGuiGraphics().flush();
        RenderSystem.disableBlend();
    }

    private static void reset() {
        wasDown = false;
        heldTicks = 0;
        activeWheel = null;
    }
}
