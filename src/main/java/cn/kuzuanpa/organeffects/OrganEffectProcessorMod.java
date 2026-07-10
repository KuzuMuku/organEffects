package cn.kuzuanpa.organeffects;

import cn.kuzuanpa.organeffects.api.extension.OrganEffectsExtensionApi;
import cn.kuzuanpa.organeffects.client.render.BowItemPropertyRegistrar;
import cn.kuzuanpa.organeffects.common.config.OrganEffectsServerConfig;
import cn.kuzuanpa.organeffects.common.data.OrganEffectData;
import cn.kuzuanpa.organeffects.common.data.PointConfigData;
import cn.kuzuanpa.organeffects.common.event.ClientTooltipHandler;
import cn.kuzuanpa.organeffects.common.event.ServerEventHandler;
import cn.kuzuanpa.organeffects.common.network.OrganEffectsNetwork;
import cn.kuzuanpa.organeffects.common.registry.OrganEffectsEnchantments;
import cn.kuzuanpa.organeffects.common.registry.OrganEffectsItems;
import cn.kuzuanpa.organeffects.common.skill.SkillManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(OrganEffectProcessorMod.MOD_ID)
public class OrganEffectProcessorMod {
    public static final String MOD_ID = "organeffects";

    public OrganEffectProcessorMod(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        OrganEffectsItems.ITEMS.register(modBus);
        OrganEffectsEnchantments.ENCHANTMENTS.register(modBus);
        modBus.addListener(this::onClientSetup);
        context.registerConfig(ModConfig.Type.COMMON, OrganEffectsServerConfig.SPEC);

        OrganEffectsExtensionApi.registerBuiltins();
        OrganEffectsNetwork.register();
        SkillManager.registerDefaults();
        MinecraftForge.EVENT_BUS.register(new ServerEventHandler());
        MinecraftForge.EVENT_BUS.register(new ClientTooltipHandler());
        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListeners);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(BowItemPropertyRegistrar::registerAllBowProperties);
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(OrganEffectData.INSTANCE);
        event.addListener(PointConfigData.INSTANCE);
    }
}
