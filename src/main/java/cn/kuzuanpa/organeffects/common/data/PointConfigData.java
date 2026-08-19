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

package cn.kuzuanpa.organeffects.common.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public final class PointConfigData extends SimplePreparableReloadListener<Map<String, PointConfigData.PointConfig>> {
    public static final PointConfigData INSTANCE = new PointConfigData();
    public static final String MARK_PREFIX = "mark:";
    public static final String TARGET_MARK_SOURCE = "target_mark";
    public static final ResourceLocation DEFAULT_MARK_ICON = ResourceLocation.fromNamespaceAndPath("organeffects", "mark/default");
    public static final int DEFAULT_MARK_PRIORITY = 10;
    public static final float DEFAULT_MARK_RENDER_SCALE = 0.3F;
    public static final double DEFAULT_MARK_RENDER_OFFSET = 0.0D;
    public static final int DEFAULT_MARK_TINT = 0xFFFFFFFF;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final String DIRECTORY = "point_config";
    private static final String MUSCULAR_STRENGTH_KEY = "organ_stat:organeffects:muscular_strength";
    private static final Set<String> DEFAULT_SYNCED_POINTS = Set.of(MUSCULAR_STRENGTH_KEY);

    private Map<String, PointConfig> pointConfigs = Map.of();

    private PointConfigData() {
    }

    @Override
    protected Map<String, PointConfig> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, PointConfig> result = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement element = GsonHelper.fromJson(GSON, reader, JsonElement.class);
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                String pointKey = GsonHelper.getAsString(object, "point", "");
                if (pointKey.isBlank()) {
                    LOGGER.warn("Skipping point config {} because it is missing a non-empty 'point' field", entry.getKey());
                    continue;
                }
                Boolean syncToClient = object.has("sync_to_client") ? GsonHelper.getAsBoolean(object, "sync_to_client") : null;
                String displayNameKey = object.has("display_name_key") ? GsonHelper.getAsString(object, "display_name_key") : null;
                int priority = object.has("priority") ? GsonHelper.getAsInt(object, "priority") : 0;
                List<String> damageTypes = readDamageTypes(object);
                boolean damageTypeWhitelist = GsonHelper.getAsBoolean(object, "damage_type_whitelist", false);
                String overflowMode = GsonHelper.getAsString(object, "overflow_mode", "spill");
                String onHitRuntime = GsonHelper.getAsString(object, "on_hit_runtime", "");
                String onBreakRuntime = GsonHelper.getAsString(object, "on_break_runtime", "");
                ResourceLocation markIcon = object.has("mark_icon")
                        ? ResourceLocation.tryParse(GsonHelper.getAsString(object, "mark_icon"))
                        : null;
                float markRenderScale = object.has("mark_render_scale")
                        ? (float) GsonHelper.getAsDouble(object, "mark_render_scale")
                        : DEFAULT_MARK_RENDER_SCALE;
                double markRenderOffset = object.has("mark_render_offset")
                        ? GsonHelper.getAsDouble(object, "mark_render_offset")
                        : DEFAULT_MARK_RENDER_OFFSET;
                int markTint = object.has("mark_tint")
                        ? parseTint(GsonHelper.getAsString(object, "mark_tint"))
                        : DEFAULT_MARK_TINT;
                result.put(pointKey, new PointConfig(
                        syncToClient,
                        displayNameKey,
                        priority,
                        damageTypes,
                        damageTypeWhitelist,
                        overflowMode,
                        onHitRuntime,
                        onBreakRuntime,
                        markIcon,
                        markRenderScale,
                        markRenderOffset,
                        markTint
                ));
            } catch (Exception exception) {
                LOGGER.warn("Failed to load point config from {}: {}", entry.getKey(), exception.getMessage());
            }
        }
        return result;
    }

    @Override
    protected void apply(Map<String, PointConfig> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        pointConfigs = Map.copyOf(prepared);
    }

    public Optional<PointConfig> getPointConfig(String pointKey) {
        return Optional.ofNullable(pointConfigs.get(pointKey));
    }

    public boolean shouldSyncToClient(String pointKey) {
        PointConfig config = pointConfigs.get(pointKey);
        if (config != null && config.syncToClient() != null) {
            return config.syncToClient();
        }
        return DEFAULT_SYNCED_POINTS.contains(pointKey);
    }

    public Map<String, Long> collectClientSyncPoints(Map<String, Long> points) {
        Map<String, Long> synced = new HashMap<>();
        for (Map.Entry<String, Long> entry : points.entrySet()) {
            if (shouldSyncToClient(entry.getKey())) {
                synced.put(entry.getKey(), entry.getValue());
            }
        }
        return synced;
    }

    public static boolean isMarkPoint(String pointKey) {
        return pointKey != null && pointKey.startsWith(MARK_PREFIX);
    }

    private static List<String> readDamageTypes(JsonObject object) {
        if (!object.has("damage_types") || !object.get("damage_types").isJsonArray()) {
            return List.of();
        }
        List<String> result = new java.util.ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(object, "damage_types")) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            String value = element.getAsString();
            if (value != null && !value.isBlank()) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private static int parseTint(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_MARK_TINT;
        }
        String hex = value.startsWith("#") ? value.substring(1) : value;
        try {
            if (hex.length() == 6) {
                return 0xFF000000 | (int) Long.parseLong(hex, 16);
            }
            if (hex.length() == 8) {
                return (int) Long.parseLong(hex, 16);
            }
        } catch (NumberFormatException ignored) {
        }
        return DEFAULT_MARK_TINT;
    }

    public record PointConfig(
            Boolean syncToClient,
            String displayNameKey,
            int priority,
            List<String> damageTypes,
            boolean damageTypeWhitelist,
            String overflowMode,
            String onHitRuntime,
            String onBreakRuntime,
            ResourceLocation markIcon,
            float markRenderScale,
            double markRenderOffset,
            int markTint
    ) {
        public PointConfig {
            damageTypes = damageTypes == null ? List.of() : List.copyOf(damageTypes);
            overflowMode = overflowMode == null || overflowMode.isBlank() ? "spill" : overflowMode;
            onHitRuntime = onHitRuntime == null ? "" : onHitRuntime;
            onBreakRuntime = onBreakRuntime == null ? "" : onBreakRuntime;
            markIcon = markIcon == null ? DEFAULT_MARK_ICON : markIcon;
            markRenderScale = Math.max(0.001F, markRenderScale);
        }
    }
}
