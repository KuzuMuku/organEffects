package cn.kuzuanpa.organeffectprocessor.api.extension;

import cn.kuzuanpa.organeffectprocessor.api.EffectDefinition;
import com.google.gson.JsonElement;

@FunctionalInterface
public interface EventFilterHandler {
    boolean matches(EffectDefinition.EventRule eventRule, OepRuntimeEvent event, String key, JsonElement value);
}
