package cn.kuzuanpa.organeffectprocessor.common.data;

import cn.kuzuanpa.organeffectprocessor.api.EffectDefinition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads organ effect definitions embedded inside organ JSON files.
 * The JSON key used is {@code organapi/organs/*.json -> effects[]}.
 * Keys in the loaded map are <b>organ definition IDs</b> (namespace:organ_name),
 * not raw file paths, so they match the IDs returned by
 * {@link cn.kuzuanpa.organapi.common.data.OrganRegistryAccess}.
 */
public class OrganEffectData extends SimplePreparableReloadListener<Map<ResourceLocation, List<EffectDefinition>>> {
    public static final OrganEffectData INSTANCE = new OrganEffectData();
    private static final String DIRECTORY = "organapi/organs";
    private static final Gson GSON = new GsonBuilder().create();
    private Map<ResourceLocation, List<EffectDefinition>> organEffects = new HashMap<>();

    private OrganEffectData() {
    }

    @Override
    protected Map<ResourceLocation, List<EffectDefinition>> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, List<EffectDefinition>> result = new HashMap<>();
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json"));

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement element = GsonHelper.fromJson(GSON, reader, JsonElement.class);
                if (element == null || !element.isJsonObject()) {
                    continue;
                }

                JsonObject obj = element.getAsJsonObject();
                if (!obj.has("effects")) {
                    continue;
                }

                JsonArray effects = GsonHelper.getAsJsonArray(obj, "effects");
                List<EffectDefinition> definitions = new ArrayList<>();
                for (JsonElement effectElement : effects) {
                    if (!effectElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject effectObj = effectElement.getAsJsonObject();
                    String trigger = readTrigger(effectObj);
                    long value = readValue(effectObj);
                    List<EffectDefinition.Grant> grants = readGrants(effectObj, entry.getKey().getNamespace());
                    definitions.add(new EffectDefinition(trigger, value, grants));
                }
                if (!definitions.isEmpty()) {
                    ResourceLocation definitionId = toDefinitionId(entry.getKey());
                    result.put(definitionId, definitions);
                }
            } catch (Exception e) {
                // Log error – silently skip malformed files during data reload
            }
        }
        return result;
    }

    @Override
    protected void apply(Map<ResourceLocation, List<EffectDefinition>> definitions, ResourceManager resourceManager, ProfilerFiller profiler) {
        organEffects = new HashMap<>(definitions);
    }

    public List<EffectDefinition> getEffectsForOrgan(ResourceLocation organId) {
        return organEffects.getOrDefault(organId, Collections.emptyList());
    }

    public Map<ResourceLocation, List<EffectDefinition>> getLoadedOrgans() {
        return organEffects;
    }

    /**
     * Converts a file ResourceLocation (e.g. {@code modid:organapi/organs/heart.json})
     * into an organ definition ID (e.g. {@code modid:heart}).
     */
    private static ResourceLocation toDefinitionId(ResourceLocation fileId) {
        String path = fileId.getPath();
        String prefix = DIRECTORY + "/";
        if (path.startsWith(prefix)) {
            path = path.substring(prefix.length());
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - 5);
        }
        return ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), path);
    }

    private static String readTrigger(JsonObject effectObj) {
        if (effectObj.has("trigger")) {
            return GsonHelper.getAsString(effectObj, "trigger");
        }
        return GsonHelper.getAsString(effectObj, "condition");
    }

    private static long readValue(JsonObject effectObj) {
        if (effectObj.has("value")) {
            return GsonHelper.getAsLong(effectObj, "value", 0L);
        }
        return GsonHelper.getAsLong(effectObj, "limit", 0L);
    }

    private static List<EffectDefinition.Grant> readGrants(JsonObject effectObj, String defaultNamespace) {
        JsonArray grantsArray;
        if (effectObj.has("grants")) {
            grantsArray = GsonHelper.getAsJsonArray(effectObj, "grants");
        } else if (effectObj.has("points")) {
            grantsArray = GsonHelper.getAsJsonArray(effectObj, "points");
        } else {
            grantsArray = new JsonArray();
        }

        List<EffectDefinition.Grant> grants = new ArrayList<>();
        for (JsonElement grantElement : grantsArray) {
            if (!grantElement.isJsonObject()) {
                continue;
            }
            JsonObject grantObj = grantElement.getAsJsonObject();
            String type = GsonHelper.getAsString(grantObj, "type");
            String id = readGrantId(grantObj, type, defaultNamespace);
            long amount = GsonHelper.getAsLong(grantObj, "amount", 0L);
            grants.add(new EffectDefinition.Grant(type, id, amount));
        }
        return grants;
    }

    private static String readGrantId(JsonObject grantObj, String type, String defaultNamespace) {
        if (grantObj.has("id")) {
            return normalizeId(GsonHelper.getAsString(grantObj, "id"), defaultNamespace);
        }
        if (grantObj.has("target")) {
            return normalizeId(GsonHelper.getAsString(grantObj, "target"), defaultNamespace);
        }
        return switch (type) {
            case "attribute" -> normalizeId(GsonHelper.getAsString(grantObj, "attribute"), defaultNamespace);
            case "skill" -> normalizeId(GsonHelper.getAsString(grantObj, "skill_name"), defaultNamespace);
            default -> normalizeId(GsonHelper.getAsString(grantObj, "key", "unknown"), defaultNamespace);
        };
    }

    private static String normalizeId(String rawId, String defaultNamespace) {
        if (rawId.indexOf(':') >= 0) {
            return rawId;
        }
        return ResourceLocation.fromNamespaceAndPath(defaultNamespace, rawId).toString();
    }
}
