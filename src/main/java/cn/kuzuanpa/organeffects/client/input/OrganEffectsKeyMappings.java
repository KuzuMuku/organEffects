package cn.kuzuanpa.organeffects.client.input;

import cn.kuzuanpa.organeffects.OrganEffectProcessorMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = OrganEffectProcessorMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class OrganEffectsKeyMappings {
    public static final KeyMapping SKILL_KEY = new KeyMapping(
            "key.organeffects.skill",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            "category.organeffects.skills"
    );

    private OrganEffectsKeyMappings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SKILL_KEY);
    }
}
