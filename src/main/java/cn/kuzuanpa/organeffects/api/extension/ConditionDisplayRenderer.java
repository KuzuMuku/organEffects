package cn.kuzuanpa.organeffects.api.extension;

import cn.kuzuanpa.organeffects.api.EffectDefinition;
import net.minecraft.network.chat.Component;

@FunctionalInterface
public interface ConditionDisplayRenderer {
    Component render(EffectDefinition.Condition condition);
}
