package cn.kuzuanpa.organeffects.api.extension;

import cn.kuzuanpa.organeffects.api.EffectDefinition;
import com.google.gson.JsonElement;

@FunctionalInterface
public interface EventFilterHandler {
    boolean matches(EffectDefinition.EventRule eventRule, OrganEffectsRuntimeEvent event, String key, JsonElement value);
}
