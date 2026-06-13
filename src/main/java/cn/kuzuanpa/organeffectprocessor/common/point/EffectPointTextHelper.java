package cn.kuzuanpa.organeffectprocessor.common.point;

import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public final class EffectPointTextHelper {
    private static final String TRANSLATION_PREFIX = "point.organeffectprocessor.";

    private EffectPointTextHelper() {
    }

    public static Component toChatLine(String pointKey, long value) {
        MutableComponent pointName = getDisplayName(pointKey)
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, getDescription(pointKey))));
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
        String translationKey = getTranslationKey(pointKey);
        MutableComponent translated = Component.translatable(translationKey);
        if (translated.getString().equals(translationKey)) {
            return Component.literal(pointKey);
        }
        return translated;
    }

    public static Component getDescription(String pointKey) {
        String translationKey = getTranslationKey(pointKey) + ".desc";
        MutableComponent translated = Component.translatable(translationKey);
        if (translated.getString().equals(translationKey)) {
            return Component.literal(pointKey);
        }
        return translated;
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
