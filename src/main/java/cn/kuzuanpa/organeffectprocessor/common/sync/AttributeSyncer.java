package cn.kuzuanpa.organeffectprocessor.common.sync;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

public class AttributeSyncer {
    private static final String ATTRIBUTE_KEY_PREFIX = "attribute:";
    private static final String MODIFIER_NAME_PREFIX = "organeffectprocessor:";

    public static void applyFromMap(Player player, Map<String, Long> oldPoints, Map<String, Long> newPoints) {
        Set<String> keys = new HashSet<>();
        keys.addAll(oldPoints.keySet());
        keys.addAll(newPoints.keySet());

        for (String key : keys) {
            if (!key.startsWith(ATTRIBUTE_KEY_PREFIX)) {
                continue;
            }
            applyAttribute(player, key, newPoints.getOrDefault(key, 0L));
        }
    }

    private static void applyAttribute(Player player, String key, long newValue) {
        String attributeName = key.substring(ATTRIBUTE_KEY_PREFIX.length());
        ResourceLocation attributeId = ResourceLocation.tryParse(attributeName);
        if (attributeId == null) {
            return;
        }

        Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(attributeId);
        if (attribute == null) {
            return;
        }

        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        UUID modifierId = UUID.nameUUIDFromBytes((MODIFIER_NAME_PREFIX + key).getBytes(StandardCharsets.UTF_8));
        instance.removeModifier(modifierId);
        if (newValue == 0L) {
            return;
        }

        instance.addPermanentModifier(new AttributeModifier(modifierId, MODIFIER_NAME_PREFIX + attributeId, (double) newValue,
                AttributeModifier.Operation.ADDITION));
    }
}
