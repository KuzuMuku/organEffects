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

package cn.kuzuanpa.organeffects.common.config;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

public final class OrganEffectsServerConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.DoubleValue DEFAULT_BOW_VELOCITY_CAP;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BOW_VELOCITY_CAPS;
    private static final ForgeConfigSpec.DoubleValue VELOCITY_CAP_BONUS_PER_ENCHANT_LEVEL;
    private static final ForgeConfigSpec.IntValue VELOCITY_CAP_ENCHANTMENT_MAX_LEVEL;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("bow_muscular_strength");
        DEFAULT_BOW_VELOCITY_CAP = builder
                .comment("Fallback maximum projectile speed for BowItem shots after muscular strength is applied.")
                .defineInRange("defaultBowVelocityCap", 3.0D, 0.0D, 64.0D);
        BOW_VELOCITY_CAPS = builder
                .comment(
                        "Per-bow projectile speed caps after muscular strength is applied.",
                        "Format: namespace:item=value",
                        "Example: minecraft:bow=3.0")
                .defineListAllowEmpty(
                        List.of("bowVelocityCaps"),
                        List.of("minecraft:bow=3.0"),
                        value -> value instanceof String string && isValidEntry(string));
        VELOCITY_CAP_BONUS_PER_ENCHANT_LEVEL = builder
                .comment("Additional projectile speed cap granted by each level of the Tension enchantment.")
                .defineInRange("velocityCapBonusPerEnchantLevel", 0.35D, 0.0D, 64.0D);
        VELOCITY_CAP_ENCHANTMENT_MAX_LEVEL = builder
                .comment("Maximum level of the Tension enchantment.")
                .defineInRange("velocityCapEnchantmentMaxLevel", 3, 1, 10);
        builder.pop();
        SPEC = builder.build();
    }

    private OrganEffectsServerConfig() {
    }

    public static double getBowVelocityCap(ItemStack bowStack) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(bowStack.getItem());
        if (itemId == null) {
            return DEFAULT_BOW_VELOCITY_CAP.get();
        }
        return parseVelocityCaps().getOrDefault(itemId.toString(), DEFAULT_BOW_VELOCITY_CAP.get());
    }

    public static double getVelocityCapBonusPerEnchantLevel() {
        return VELOCITY_CAP_BONUS_PER_ENCHANT_LEVEL.get();
    }

    public static int getVelocityCapEnchantmentMaxLevel() {
        return VELOCITY_CAP_ENCHANTMENT_MAX_LEVEL.get();
    }

    private static Map<String, Double> parseVelocityCaps() {
        Map<String, Double> parsed = new HashMap<>();
        for (String entry : BOW_VELOCITY_CAPS.get()) {
            int split = entry.indexOf('=');
            if (split <= 0 || split >= entry.length() - 1) {
                continue;
            }
            String itemId = entry.substring(0, split).trim();
            String valuePart = entry.substring(split + 1).trim();
            try {
                ResourceLocation id = ResourceLocation.tryParse(itemId);
                if (id == null) {
                    continue;
                }
                double value = Double.parseDouble(valuePart);
                if (value >= 0.0D) {
                    parsed.put(id.toString(), value);
                }
            } catch (NumberFormatException ignored) {
                // Invalid entries are rejected by config validation and skipped defensively here.
            }
        }
        return parsed;
    }

    private static boolean isValidEntry(String entry) {
        int split = entry.indexOf('=');
        if (split <= 0 || split >= entry.length() - 1) {
            return false;
        }
        if (ResourceLocation.tryParse(entry.substring(0, split).trim()) == null) {
            return false;
        }
        try {
            return Double.parseDouble(entry.substring(split + 1).trim()) >= 0.0D;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
