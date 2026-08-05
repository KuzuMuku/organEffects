/*
 * This class was created by <kuzuanpa>. It is distributed as
 * part of the organEffects Mod. Get the Source Code in github:
 * https://github.com/KuzuMuku/organEffects
 *
 * organEffects is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.

 * organEffects is Open Source and distributed under the
 * AGPLv3 License: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 */

package cn.kuzuanpa.organeffects.common.sync;

import cn.kuzuanpa.organeffects.common.debug.OrganEffectsDebug;
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
    private static final String MODIFIER_NAME_PREFIX = "organeffects:";
    private static final String MOVEMENT_SPEED_ATTRIBUTE = "minecraft:generic.movement_speed";

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
            OrganEffectsDebug.trace(player, "attribute skipped missing instance id=%s", attributeId);
            return;
        }

        UUID modifierId = UUID.nameUUIDFromBytes((MODIFIER_NAME_PREFIX + key).getBytes(StandardCharsets.UTF_8));
        instance.removeModifier(modifierId);
        if (newValue == 0L) {
            return;
        }

        instance.addPermanentModifier(new AttributeModifier(modifierId, MODIFIER_NAME_PREFIX + attributeId,
                resolveModifierAmount(attributeId, newValue), resolveOperation(attributeId)));
    }

    private static double resolveModifierAmount(ResourceLocation attributeId, long newValue) {
        if (MOVEMENT_SPEED_ATTRIBUTE.equals(attributeId.toString())) {
            return newValue * 0.1D;
        }
        return (double) newValue;
    }

    private static AttributeModifier.Operation resolveOperation(ResourceLocation attributeId) {
        if (MOVEMENT_SPEED_ATTRIBUTE.equals(attributeId.toString())) {
            return AttributeModifier.Operation.MULTIPLY_TOTAL;
        }
        return AttributeModifier.Operation.ADDITION;
    }
}
