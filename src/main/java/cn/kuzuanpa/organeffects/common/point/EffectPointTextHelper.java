package cn.kuzuanpa.organeffects.common.point;

import cn.kuzuanpa.organeffects.common.data.PointConfigData;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;

public final class EffectPointTextHelper {
    private static final String TRANSLATION_PREFIX = "point.organeffects.";

    private EffectPointTextHelper() {
    }

    public static Component toChatLine(String pointKey, long value) {
        return toChatLine(pointKey, value, Map.of());
    }

    public static Component toChatLine(String pointKey, long value, Map<String, Long> sourceBreakdown) {
        MutableComponent pointName = getDisplayName(pointKey)
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, getDescription(pointKey, sourceBreakdown))));
        return Component.literal("- ")
                .append(pointName)
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(Long.toString(value)).withStyle(ChatFormatting.GOLD));
    }

    public static Component getGroupHeader(String groupKey) {
        return Component.translatable("message.organeffects.points.group." + sanitizeSegment(groupKey))
                .withStyle(ChatFormatting.AQUA);
    }

    public static String getPointType(String pointKey) {
        int separator = pointKey.indexOf(':');
        return separator < 0 ? "unknown" : pointKey.substring(0, separator);
    }

    public static MutableComponent getDisplayName(String pointKey) {
        MutableComponent defaultName = getDefaultDisplayName(pointKey);
        String configuredDisplayNameKey = PointConfigData.INSTANCE.getPointConfig(pointKey)
                .map(PointConfigData.PointConfig::displayNameKey)
                .filter(key -> key != null && !key.isBlank())
                .orElse(null);
        if (configuredDisplayNameKey != null) {
            return Component.translatableWithFallback(configuredDisplayNameKey, defaultName.getString());
        }
        return defaultName;
    }

    private static MutableComponent getDefaultDisplayName(String pointKey) {
        List<String> translationKeys = getTranslationKeys(pointKey);
        if (!translationKeys.isEmpty()) {
            return Component.translatableWithFallback(translationKeys.get(0), fallbackName(pointKey));
        }
        Attribute attribute = getAttribute(pointKey);
        if (attribute != null) {
            return Component.translatable(attribute.getDescriptionId());
        }
        return Component.literal(pointKey);
    }

    public static Component getDescription(String pointKey) {
        return getDescription(pointKey, Map.of());
    }

    public static Component getDescription(String pointKey, Map<String, Long> sourceBreakdown) {
        List<String> translationKeys = getTranslationKeys(pointKey);
        Component base;
        if (!translationKeys.isEmpty()) {
            base = Component.translatableWithFallback(translationKeys.get(0) + ".desc", fallbackDescription(pointKey));
        } else {
            base = Component.literal(fallbackDescription(pointKey));
        }
        if (sourceBreakdown.isEmpty()) {
            return base;
        }

        MutableComponent combined = base.copy().append(Component.literal("\n").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable("message.organeffects.points.sources").withStyle(ChatFormatting.DARK_AQUA));
        sourceBreakdown.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> combined.append(Component.literal("\n- " + entry.getKey() + ": " + entry.getValue()).withStyle(ChatFormatting.BLUE)));
        return combined;
    }

    private static String fallbackDescription(String pointKey) {
        return fallbackName(pointKey);
    }

    private static Attribute getAttribute(String pointKey) {
        if (!pointKey.startsWith("attribute:")) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(pointKey.substring("attribute:".length()));
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.ATTRIBUTE.get(id);
    }

    private static List<String> getTranslationKeys(String pointKey) {
        int separator = pointKey.indexOf(':');
        if (separator < 0 || separator == pointKey.length() - 1) {
            return List.of(TRANSLATION_PREFIX + "unknown." + sanitizeSegment(pointKey));
        }

        String type = sanitizeSegment(pointKey.substring(0, separator));
        String rawId = pointKey.substring(separator + 1);
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            return List.of(TRANSLATION_PREFIX + type + ".unknown." + sanitizeSegment(rawId));
        }
        List<String> keys = new ArrayList<>();
        boolean rawHadNamespace = rawId.contains(":");
        if (!rawHadNamespace) {
            keys.add(TRANSLATION_PREFIX + type + ".organeffects." + sanitizeSegment(id.getPath()));
        }
        keys.add(TRANSLATION_PREFIX + type + "." + sanitizeSegment(id.getNamespace()) + "." + sanitizeSegment(id.getPath()));
        keys.add(TRANSLATION_PREFIX + type + "." + sanitizeSegment(id.getPath()));
        return keys;
    }

    private static String fallbackName(String pointKey) {
        int separator = pointKey.indexOf(':');
        if (separator < 0 || separator == pointKey.length() - 1) {
            return pointKey;
        }
        String rawId = pointKey.substring(separator + 1);
        int namespaceSeparator = rawId.indexOf(':');
        String path = namespaceSeparator >= 0 && namespaceSeparator < rawId.length() - 1
                ? rawId.substring(namespaceSeparator + 1)
                : rawId;
        return humanizePath(path);
    }

    private static String humanizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String[] parts = path.replace('/', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.length() > 0 ? builder.toString() : path;
    }

    private static String sanitizeSegment(String value) {
        return value.replace(':', '.').replace('/', '.');
    }
}
