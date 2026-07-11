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
                result.put(pointKey, new PointConfig(syncToClient, displayNameKey));
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

    public record PointConfig(Boolean syncToClient, String displayNameKey) {
    }
}
