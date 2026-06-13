package cn.kuzuanpa.organeffectprocessor.common.point;

import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;

public final class EffectPointTextHelper {
    private static final String TRANSLATION_PREFIX = "point.organeffectprocessor.";

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
        return Component.translatable("message.organeffectprocessor.points.group." + sanitizeSegment(groupKey))
                .withStyle(ChatFormatting.AQUA);
    }

    public static String getPointType(String pointKey) {
        int separator = pointKey.indexOf(':');
        return separator < 0 ? "unknown" : pointKey.substring(0, separator);
    }

    public static MutableComponent getDisplayName(String pointKey) {
        Attribute attribute = getAttribute(pointKey);
        if (attribute != null) {
            return Component.translatable(attribute.getDescriptionId());
        }

        String translationKey = getTranslationKey(pointKey);
        MutableComponent translated = Component.translatable(translationKey);
        if (translated.getString().equals(translationKey)) {
            return Component.literal(pointKey);
        }
        return translated;
    }

    public static Component getDescription(String pointKey) {
        return getDescription(pointKey, Map.of());
    }

    public static Component getDescription(String pointKey, Map<String, Long> sourceBreakdown) {
        String translationKey = getTranslationKey(pointKey) + ".desc";
        MutableComponent translated = Component.translatable(translationKey);
        Component base = translated.getString().equals(translationKey)
                ? fallbackDescription(pointKey)
                : translated;
        if (sourceBreakdown.isEmpty()) {
            return base;
        }

        MutableComponent combined = base.copy().append(Component.literal("\n").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable("message.organeffectprocessor.points.sources").withStyle(ChatFormatting.DARK_AQUA));
        sourceBreakdown.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> combined.append(Component.literal("\n- " + entry.getKey() + ": " + entry.getValue()).withStyle(ChatFormatting.BLUE)));
        return combined;
    }

    private static Component fallbackDescription(String pointKey) {
        Attribute attribute = getAttribute(pointKey);
        if (attribute != null) {
            return Component.translatable(attribute.getDescriptionId());
        }
        return Component.literal(pointKey);
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

    private static String getTranslationKey(String pointKey) {
        int separator = pointKey.indexOf(':');
        if (separator < 0 || separator == pointKey.length() - 1) {
            return TRANSLATION_PREFIX + "unknown." + sanitizeSegment(pointKey);
        }

        String type = sanitizeSegment(pointKey.substring(0, separator));
        ResourceLocation id = ResourceLocation.tryParse(pointKey.substring(separator + 1));
        if (id == null) {
            return TRANSLATION_PREFIX + type + ".unknown." + sanitizeSegment(pointKey.substring(separator + 1));
        }
        return TRANSLATION_PREFIX + type + "." + sanitizeSegment(id.getNamespace()) + "." + sanitizeSegment(id.getPath());
    }

    private static String sanitizeSegment(String value) {
        return value.replace(':', '.').replace('/', '.');
    }
}
