package cn.kuzuanpa.organeffectprocessor;

import cn.kuzuanpa.organeffectprocessor.api.extension.OepExtensionApi;
import cn.kuzuanpa.organeffectprocessor.client.render.BowItemPropertyRegistrar;
import cn.kuzuanpa.organeffectprocessor.common.config.OepServerConfig;
import cn.kuzuanpa.organeffectprocessor.common.data.OrganEffectData;
import cn.kuzuanpa.organeffectprocessor.common.data.PointConfigData;
import cn.kuzuanpa.organeffectprocessor.common.event.ClientTooltipHandler;
import cn.kuzuanpa.organeffectprocessor.common.event.ServerEventHandler;
import cn.kuzuanpa.organeffectprocessor.common.network.OepNetwork;
import cn.kuzuanpa.organeffectprocessor.common.registry.OepEnchantments;
import cn.kuzuanpa.organeffectprocessor.common.registry.OepItems;
import cn.kuzuanpa.organeffectprocessor.common.skill.SkillManager;
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
    public static final String MOD_ID = "organeffectprocessor";

    public OrganEffectProcessorMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        OepItems.ITEMS.register(modBus);
        OepEnchantments.ENCHANTMENTS.register(modBus);
        modBus.addListener(this::onClientSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, OepServerConfig.SPEC);

        OepExtensionApi.registerBuiltins();
        OepNetwork.register();
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
