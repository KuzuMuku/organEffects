package cn.kuzuanpa.organeffectprocessor.client.input;

import cn.kuzuanpa.organeffectprocessor.client.screen.SkillWheelScreen;
import cn.kuzuanpa.organeffectprocessor.common.network.CastSelectedSkillC2SPacket;
import cn.kuzuanpa.organeffectprocessor.common.network.OepNetwork;
import cn.kuzuanpa.organeffectprocessor.common.skill.SkillManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = cn.kuzuanpa.organeffectprocessor.OrganEffectProcessorMod.MOD_ID)
public final class SkillKeyHandler {
    private static final int LONG_PRESS_TICKS = 10;
    private static boolean wasDown;
    private static int heldTicks;
    private static boolean wheelOpened;

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

        boolean isDown = OepKeyMappings.SKILL_KEY.isDown();
        if (isDown) {
            heldTicks++;
            if (!wheelOpened && heldTicks >= LONG_PRESS_TICKS && minecraft.screen == null && !SkillManager.getAvailableSkills(player).isEmpty()) {
                minecraft.setScreen(new SkillWheelScreen(player));
                wheelOpened = true;
            }
        }

        if (!isDown && wasDown) {
            if (wheelOpened) {
                if (minecraft.screen instanceof SkillWheelScreen skillWheelScreen) {
                    skillWheelScreen.confirmSelection();
                } else {
                    minecraft.setScreen(null);
                }
            } else if (heldTicks > 0 && heldTicks < LONG_PRESS_TICKS) {
                OepNetwork.sendToServer(new CastSelectedSkillC2SPacket());
            }
            reset();
        }
        wasDown = isDown;
    }

    private static void reset() {
        wasDown = false;
        heldTicks = 0;
        wheelOpened = false;
    }
}
