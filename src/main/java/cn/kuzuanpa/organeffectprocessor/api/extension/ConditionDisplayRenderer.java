package cn.kuzuanpa.organeffectprocessor.api.extension;

import cn.kuzuanpa.organeffectprocessor.api.EffectDefinition;
import net.minecraft.network.chat.Component;

@FunctionalInterface
public interface ConditionDisplayRenderer {
    Component render(EffectDefinition.Condition condition);
}
