package cn.kuzuanpa.organeffectprocessor.common.data;

import cn.kuzuanpa.organeffectprocessor.api.EffectDefinition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

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
    private static final Logger LOGGER = LogUtils.getLogger();
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
                for (int index = 0; index < effects.size(); index++) {
                    JsonElement effectElement = effects.get(index);
                    if (!effectElement.isJsonObject()) {
                        LOGGER.warn("Skipping non-object effect entry {} in {}", index, entry.getKey());
                        continue;
                    }
                    try {
                        definitions.add(readEffect(effectElement.getAsJsonObject(), entry.getKey(), index));
                    } catch (Exception e) {
                        LOGGER.warn("Skipping malformed effect entry {} in {}: {}", index, entry.getKey(), e.getMessage());
                    }
                }
                if (!definitions.isEmpty()) {
                    ResourceLocation definitionId = toDefinitionId(entry.getKey());
                    result.put(definitionId, definitions);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load organ effect data from {}: {}", entry.getKey(), e.getMessage());
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

    private static EffectDefinition readEffect(JsonObject effectObj, ResourceLocation fileId, int effectIndex) {
        List<EffectDefinition.Condition> conditions = readConditions(effectObj, fileId, effectIndex);
        List<EffectDefinition.Grant> grants = readGrants(effectObj, fileId.getNamespace());
        List<EffectDefinition.EventRule> events = readEvents(effectObj, fileId, effectIndex, fileId.getNamespace());
        return new EffectDefinition(conditions, grants, events);
    }

    private static List<EffectDefinition.Condition> readConditions(JsonObject effectObj, ResourceLocation fileId, int effectIndex) {
        if (!effectObj.has("conditions")) {
            throw new IllegalArgumentException("Effect " + effectIndex + " in " + fileId + " is missing conditions");
        }
        JsonArray conditionsArray = GsonHelper.getAsJsonArray(effectObj, "conditions");
        List<EffectDefinition.Condition> conditions = new ArrayList<>();
        for (int index = 0; index < conditionsArray.size(); index++) {
            JsonElement conditionElement = conditionsArray.get(index);
            if (!conditionElement.isJsonObject()) {
                LOGGER.warn("Skipping non-object condition {} for effect {} in {}", index, effectIndex, fileId);
                continue;
            }
            conditions.add(readConditionObject(conditionElement.getAsJsonObject(), fileId, effectIndex, index));
        }
        return conditions;
    }

    private static EffectDefinition.Condition readConditionObject(JsonObject conditionObj, ResourceLocation fileId, int effectIndex, int conditionIndex) {
        String type = GsonHelper.getAsString(conditionObj, "type");
        try {
            return switch (type) {
                case "static" -> new EffectDefinition.Condition("static", null, null, null, null, null, null, null, null, null, null, null);
                case "slot_index" -> new EffectDefinition.Condition(
                        "slot_index",
                        readOperator(conditionObj),
                        GsonHelper.getAsLong(conditionObj, "value"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );
                case "distance_to_edge" -> new EffectDefinition.Condition(
                        "distance_to_edge",
                        readOperator(conditionObj),
                        GsonHelper.getAsLong(conditionObj, "value"),
                        null,
                        null,
                        GsonHelper.getAsString(conditionObj, "edge"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );
                case "weather" -> new EffectDefinition.Condition(
                        "weather",
                        null,
                        null,
                        null,
                        null,
                        null,
                        GsonHelper.getAsString(conditionObj, "value"),
                        null,
                        null,
                        null,
                        null,
                        null
                );
                case "time" -> new EffectDefinition.Condition(
                        "time",
                        conditionObj.has("op") ? GsonHelper.getAsString(conditionObj, "op") : null,
                        conditionObj.has("value") ? GsonHelper.getAsLong(conditionObj, "value") : null,
                        conditionObj.has("min") ? GsonHelper.getAsLong(conditionObj, "min") : null,
                        conditionObj.has("max") ? GsonHelper.getAsLong(conditionObj, "max") : null,
                        null,
                        null,
                        conditionObj.has("mode") ? GsonHelper.getAsString(conditionObj, "mode") : null,
                        null,
                        null,
                        null,
                        null
                );
                case "has_organ" -> new EffectDefinition.Condition(
                        "has_organ",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        GsonHelper.getAsString(conditionObj, "scope"),
                        GsonHelper.getAsString(conditionObj, "body_part", null),
                        conditionObj.has("slot") ? GsonHelper.getAsInt(conditionObj, "slot") : null,
                        normalizeId(GsonHelper.getAsString(conditionObj, "organ"), fileId.getNamespace(), false)
                );
                default -> throw new IllegalArgumentException("Unknown condition type: " + type);
            };
        } catch (Exception e) {
            throw new IllegalArgumentException("Condition " + conditionIndex + " of effect " + effectIndex + " in " + fileId + " is invalid: " + e.getMessage(), e);
        }
    }

    private static String readOperator(JsonObject conditionObj) {
        return GsonHelper.getAsString(conditionObj, "op");
    }

    private static List<EffectDefinition.Grant> readGrants(JsonObject effectObj, String defaultNamespace) {
        if (!effectObj.has("grants")) {
            return List.of();
        }
        JsonArray grantsArray = GsonHelper.getAsJsonArray(effectObj, "grants");
        List<EffectDefinition.Grant> grants = new ArrayList<>();
        for (JsonElement grantElement : grantsArray) {
            if (!grantElement.isJsonObject()) {
                continue;
            }
            JsonObject grantObj = grantElement.getAsJsonObject();
            String type = GsonHelper.getAsString(grantObj, "type");
            String id = readPointId(grantObj, type, defaultNamespace);
            long amount = GsonHelper.getAsLong(grantObj, "amount", 0L);
            grants.add(new EffectDefinition.Grant(type, id, amount));
        }
        return grants;
    }

    private static List<EffectDefinition.EventRule> readEvents(JsonObject effectObj, ResourceLocation fileId, int effectIndex, String defaultNamespace) {
        if (!effectObj.has("events")) {
            return List.of();
        }
        JsonArray eventsArray = GsonHelper.getAsJsonArray(effectObj, "events");
        List<EffectDefinition.EventRule> events = new ArrayList<>();
        for (int index = 0; index < eventsArray.size(); index++) {
            JsonElement eventElement = eventsArray.get(index);
            if (!eventElement.isJsonObject()) {
                LOGGER.warn("Skipping non-object event {} for effect {} in {}", index, effectIndex, fileId);
                continue;
            }
            try {
                events.add(readEventRule(eventElement.getAsJsonObject(), defaultNamespace));
            } catch (Exception e) {
                throw new IllegalArgumentException("Event " + index + " of effect " + effectIndex + " in " + fileId + " is invalid: " + e.getMessage(), e);
            }
        }
        return events;
    }

    private static EffectDefinition.EventRule readEventRule(JsonObject eventObj, String defaultNamespace) {
        String type = GsonHelper.getAsString(eventObj, "type");
        Long distance = eventObj.has("distance") ? GsonHelper.getAsLong(eventObj, "distance") : null;
        String source = GsonHelper.getAsString(eventObj, "source", null);
        String item = eventObj.has("item") ? normalizeId(GsonHelper.getAsString(eventObj, "item"), defaultNamespace, false) : null;
        String itemTag = GsonHelper.getAsString(eventObj, "item_tag", null);
        String block = eventObj.has("block") ? normalizeId(GsonHelper.getAsString(eventObj, "block"), defaultNamespace, false) : null;
        String blockTag = GsonHelper.getAsString(eventObj, "block_tag", null);
        boolean foodOnly = GsonHelper.getAsBoolean(eventObj, "food_only", false);
        List<EffectDefinition.PointMutation> addPoints = readPointMutations(eventObj, "add_points", defaultNamespace);
        List<EffectDefinition.PointMutation> consumePoints = readPointMutations(eventObj, "consume_points", defaultNamespace);
        List<EffectDefinition.BonusAction> actions = readBonusActions(eventObj, defaultNamespace);
        return new EffectDefinition.EventRule(type, distance, source, item, itemTag, block, blockTag, foodOnly, addPoints, consumePoints, actions);
    }

    private static List<EffectDefinition.PointMutation> readPointMutations(JsonObject eventObj, String fieldName, String defaultNamespace) {
        if (!eventObj.has(fieldName)) {
            return List.of();
        }
        JsonArray mutationsArray = GsonHelper.getAsJsonArray(eventObj, fieldName);
        List<EffectDefinition.PointMutation> mutations = new ArrayList<>();
        for (JsonElement mutationElement : mutationsArray) {
            if (!mutationElement.isJsonObject()) {
                continue;
            }
            JsonObject mutationObj = mutationElement.getAsJsonObject();
            String type = GsonHelper.getAsString(mutationObj, "type");
            String id = readPointId(mutationObj, type, defaultNamespace);
            long amount = GsonHelper.getAsLong(mutationObj, "amount", 0L);
            String source = GsonHelper.getAsString(mutationObj, "source", null);
            EffectDefinition.ChanceConfig chance = readChanceConfig(mutationObj);
            mutations.add(new EffectDefinition.PointMutation(type, id, amount, source, chance));
        }
        return mutations;
    }

    private static EffectDefinition.ChanceConfig readChanceConfig(JsonObject mutationObj) {
        if (!mutationObj.has("chance")) {
            return null;
        }
        JsonObject chanceObj = GsonHelper.getAsJsonObject(mutationObj, "chance");
        Double base = chanceObj.has("base") ? GsonHelper.getAsDouble(chanceObj, "base") : null;
        Double luckyStep = chanceObj.has("lucky_step") ? GsonHelper.getAsDouble(chanceObj, "lucky_step") : null;
        Double max = chanceObj.has("max") ? GsonHelper.getAsDouble(chanceObj, "max") : null;
        return new EffectDefinition.ChanceConfig(base, luckyStep, max);
    }

    private static List<EffectDefinition.BonusAction> readBonusActions(JsonObject eventObj, String defaultNamespace) {
        if (!eventObj.has("actions")) {
            return List.of();
        }
        JsonArray actionArray = GsonHelper.getAsJsonArray(eventObj, "actions");
        List<EffectDefinition.BonusAction> actions = new ArrayList<>();
        for (JsonElement actionElement : actionArray) {
            if (!actionElement.isJsonObject()) {
                continue;
            }
            JsonObject actionObj = actionElement.getAsJsonObject();
            String type = GsonHelper.getAsString(actionObj, "type");
            double amountPerPoint = GsonHelper.getAsDouble(actionObj, "amount_per_point", 0.0D);
            String pointType = GsonHelper.getAsString(actionObj, "point_type", "counter");
            String pointId = readPointId(actionObj, pointType, defaultNamespace, "point_id");
            String source = GsonHelper.getAsString(actionObj, "source", null);
            long maxConsume = GsonHelper.getAsLong(actionObj, "max_consume", Long.MAX_VALUE);
            actions.add(new EffectDefinition.BonusAction(type, amountPerPoint, pointType, pointId, source, maxConsume));
        }
        return actions;
    }

    private static String readPointId(JsonObject obj, String type, String defaultNamespace) {
        return readPointId(obj, type, defaultNamespace, null);
    }

    private static String readPointId(JsonObject obj, String type, String defaultNamespace, String explicitIdField) {
        if (explicitIdField != null && obj.has(explicitIdField)) {
            return normalizeId(GsonHelper.getAsString(obj, explicitIdField), defaultNamespace, "attribute".equals(type));
        }
        if (obj.has("id")) {
            return normalizeId(GsonHelper.getAsString(obj, "id"), defaultNamespace, "attribute".equals(type));
        }
        return switch (type) {
            case "attribute" -> normalizeId(GsonHelper.getAsString(obj, "attribute"), defaultNamespace, true);
            case "skill" -> normalizeId(GsonHelper.getAsString(obj, "skill_name"), defaultNamespace, false);
            default -> normalizeId(GsonHelper.getAsString(obj, "key", "unknown"), defaultNamespace, false);
        };
    }

    private static String normalizeId(String rawId, String defaultNamespace, boolean preferMinecraftNamespace) {
        if (rawId.indexOf(':') >= 0) {
            return rawId;
        }
        String namespace = preferMinecraftNamespace ? "minecraft" : defaultNamespace;
        return ResourceLocation.fromNamespaceAndPath(namespace, rawId).toString();
    }
}
