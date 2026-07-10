package cn.kuzuanpa.organeffects.common.capability;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

public final class EffectCapabilities {
    public static final Capability<IEffectHolder> EFFECT_HOLDER = CapabilityManager.get(new CapabilityToken<IEffectHolder>() {});

    private EffectCapabilities() {
    }
}
