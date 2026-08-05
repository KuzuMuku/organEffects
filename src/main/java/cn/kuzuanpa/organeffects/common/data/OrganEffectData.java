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

import cn.kuzuanpa.organeffects.api.EffectDefinition;
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
import java.util.Set;
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
    private static final Set<String> CONDITION_KEYS = Set.of(
            "type", "config", "op", "value", "min", "max", "edge", "mode", "scope", "body_part", "slot", "organ", "biome_tag", "tag", "block", "block_tag",
            "custom_display_key"
    );
    private static final Set<String> EVENT_RULE_KEYS = Set.of(
            "type", "config", "distance", "source", "item", "item_tag", "block", "block_tag", "food_only", "add_points", "consume_points", "actions",
            "hidden", "custom_display_key"
    );
    private static final Set<String> BONUS_ACTION_KEYS = Set.of(
            "type", "config", "amount", "point_type", "point_id", "id", "attribute", "skill_name", "key", "source", "max_consume",
            "consume_points", "effect", "duration_ticks", "amplifier", "target", "items", "rolls", "unique", "drop_if_full",
            "chance", "hidden", "custom_display_key"
    );
    private Map<ResourceLocation, List<EffectDefinition>> organEffects = new HashMap<>();
    private Map<ResourceLocation, List<List<DerivedGrantRule>>> derivedGrantRules = new HashMap<>();

    private OrganEffectData() {
    }

    @Override
    protected Map<ResourceLocation, List<EffectDefinition>> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, List<EffectDefinition>> result = new HashMap<>();
        Map<ResourceLocation, List<List<DerivedGrantRule>>> derivedRulesResult = new HashMap<>();
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
                List<List<DerivedGrantRule>> effectDerivedRules = new ArrayList<>();
                for (int index = 0; index < effects.size(); index++) {
                    JsonElement effectElement = effects.get(index);
                    if (!effectElement.isJsonObject()) {
                        LOGGER.warn("Skipping non-object effect entry {} in {}", index, entry.getKey());
                        continue;
                    }
                    try {
                        LoadedEffect loadedEffect = readEffect(effectElement.getAsJsonObject(), entry.getKey(), index);
                        definitions.add(loadedEffect.definition());
                        effectDerivedRules.add(loadedEffect.derivedGrantRules());
                    } catch (Exception e) {
                        LOGGER.warn("Skipping malformed effect entry {} in {}: {}", index, entry.getKey(), e.getMessage());
                    }
                }
                if (!definitions.isEmpty()) {
                    ResourceLocation definitionId = toDefinitionId(entry.getKey());
                    result.put(definitionId, definitions);
                    derivedRulesResult.put(definitionId, effectDerivedRules);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load organ effect data from {}: {}", entry.getKey(), e.getMessage());
            }
        }
        preparedDerivedGrantRules = derivedRulesResult;
        return result;
    }

    private Map<ResourceLocation, List<List<DerivedGrantRule>>> preparedDerivedGrantRules = Map.of();

    @Override
    protected void apply(Map<ResourceLocation, List<EffectDefinition>> definitions, ResourceManager resourceManager, ProfilerFiller profiler) {
        organEffects = new HashMap<>(definitions);
        derivedGrantRules = new HashMap<>(preparedDerivedGrantRules);
        preparedDerivedGrantRules = Map.of();
    }

    public List<EffectDefinition> getEffectsForOrgan(ResourceLocation organId) {
        return organEffects.getOrDefault(organId, Collections.emptyList());
    }

    public Map<ResourceLocation, List<EffectDefinition>> getLoadedOrgans() {
        return organEffects;
    }

    public List<DerivedGrantRule> getDerivedGrantRulesForEffect(ResourceLocation organId, int effectIndex) {
        List<List<DerivedGrantRule>> byEffect = derivedGrantRules.getOrDefault(organId, List.of());
        if (effectIndex < 0 || effectIndex >= byEffect.size()) {
            return List.of();
        }
        return byEffect.get(effectIndex);
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

    private static LoadedEffect readEffect(JsonObject effectObj, ResourceLocation fileId, int effectIndex) {
        List<EffectDefinition.Condition> conditions = readConditions(effectObj, fileId, effectIndex);
        GrantReadResult grantResult = readGrants(effectObj, fileId.getNamespace());
        List<EffectDefinition.EventRule> events = readEvents(effectObj, fileId, effectIndex, fileId.getNamespace());
        List<EffectDefinition.BonusAction> executions = readExecutions(effectObj, fileId.getNamespace());
        return new LoadedEffect(new EffectDefinition(conditions, grantResult.grants(), events, executions), grantResult.derivedGrantRules());
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
        JsonObject config = readConditionConfig(conditionObj, fileId.getNamespace());
        JsonObject extra = collectExtra(conditionObj, CONDITION_KEYS);
        try {
            return new EffectDefinition.Condition(type, config, extra);
        } catch (Exception e) {
            throw new IllegalArgumentException("Condition " + conditionIndex + " of effect " + effectIndex + " in " + fileId + " is invalid: " + e.getMessage(), e);
        }
    }

    private static JsonObject readConditionConfig(JsonObject conditionObj, String defaultNamespace) {
        JsonObject config = readConfigObject(conditionObj).deepCopy();
        copyString(conditionObj, config, "op");
        copyLong(conditionObj, config, "value");
        copyLong(conditionObj, config, "min");
        copyLong(conditionObj, config, "max");
        copyString(conditionObj, config, "edge");
        copyString(conditionObj, config, "mode");
        copyString(conditionObj, config, "scope");
        copyString(conditionObj, config, "body_part");
        copyInt(conditionObj, config, "slot");
        copyString(conditionObj, config, "custom_display_key");

        if (conditionObj.has("organ") && !config.has("organ")) {
            config.addProperty("organ", normalizeId(GsonHelper.getAsString(conditionObj, "organ"), defaultNamespace, false));
        }
        if (conditionObj.has("value") && !config.has("value_id") && shouldNormalizeValueId(GsonHelper.getAsString(conditionObj, "type"))) {
            config.addProperty("value_id", normalizeId(GsonHelper.getAsString(conditionObj, "value"), defaultNamespace, false));
        }
        if (conditionObj.has("biome_tag") && !config.has("biome_tag")) {
            config.addProperty("biome_tag", normalizeId(GsonHelper.getAsString(conditionObj, "biome_tag"), defaultNamespace, false));
        }
        if (conditionObj.has("tag") && !config.has("biome_tag")) {
            config.addProperty("biome_tag", normalizeId(GsonHelper.getAsString(conditionObj, "tag"), defaultNamespace, false));
        }
        if (conditionObj.has("block") && !config.has("block")) {
            config.addProperty("block", normalizeId(GsonHelper.getAsString(conditionObj, "block"), defaultNamespace, false));
        }
        if (conditionObj.has("block_tag") && !config.has("block_tag")) {
            config.addProperty("block_tag", GsonHelper.getAsString(conditionObj, "block_tag"));
        }
        if (conditionObj.has("value") && !config.has("weather") && "weather".equals(GsonHelper.getAsString(conditionObj, "type"))) {
            config.addProperty("weather", GsonHelper.getAsString(conditionObj, "value"));
        }
        return config;
    }

    private static boolean shouldNormalizeValueId(String type) {
        return "biome".equals(type) || "dimid".equals(type);
    }

    private static void copyString(JsonObject source, JsonObject target, String key) {
        if (source.has(key) && !target.has(key)) {
            target.addProperty(key, GsonHelper.getAsString(source, key));
        }
    }

    private static void copyLong(JsonObject source, JsonObject target, String key) {
        if (source.has(key) && !target.has(key)) {
            target.addProperty(key, GsonHelper.getAsLong(source, key));
        }
    }

    private static void copyInt(JsonObject source, JsonObject target, String key) {
        if (source.has(key) && !target.has(key)) {
            target.addProperty(key, GsonHelper.getAsInt(source, key));
        }
    }

    private static JsonObject readConfigObject(JsonObject source) {
        if (!source.has("config") || !source.get("config").isJsonObject()) {
            return new JsonObject();
        }
        return GsonHelper.getAsJsonObject(source, "config");
    }

    private static GrantReadResult readGrants(JsonObject effectObj, String defaultNamespace) {
        if (!effectObj.has("grants")) {
            return new GrantReadResult(List.of(), List.of());
        }
        JsonArray grantsArray = GsonHelper.getAsJsonArray(effectObj, "grants");
        List<EffectDefinition.Grant> grants = new ArrayList<>();
        List<DerivedGrantRule> derivedRules = new ArrayList<>();
        for (JsonElement grantElement : grantsArray) {
            if (!grantElement.isJsonObject()) {
                continue;
            }
            JsonObject grantObj = grantElement.getAsJsonObject();
            String type = GsonHelper.getAsString(grantObj, "type");
            long amount = GsonHelper.getAsLong(grantObj, "amount", 0L);
            DerivedGrantRule derivedRule = readDerivedGrantRule(grantObj, amount, defaultNamespace);
            if (derivedRule != null) {
                derivedRules.add(derivedRule);
                continue;
            }
            String id = readPointId(grantObj, type, defaultNamespace);
            boolean hidden = GsonHelper.getAsBoolean(grantObj, "hidden", false);
            grants.add(new EffectDefinition.Grant(type, id, amount, hidden));
        }
        return new GrantReadResult(grants, derivedRules);
    }

    private static DerivedGrantRule readDerivedGrantRule(JsonObject grantObj, long amount, String defaultNamespace) {
        if (!"derived".equals(GsonHelper.getAsString(grantObj, "type"))) {
            return null;
        }
        JsonObject config = readConfigObject(grantObj).deepCopy();
        String fromType = GsonHelper.getAsString(config, "from_type", null);
        String targetType = GsonHelper.getAsString(config, "target_type", null);
        Long per = config.has("per") ? GsonHelper.getAsLong(config, "per") : null;
        if (fromType == null || targetType == null || per == null) {
            throw new IllegalArgumentException("Derived grant requires config.from_type, config.target_type, and config.per");
        }
        if (per <= 0L) {
            throw new IllegalArgumentException("Derived grant per must be > 0");
        }
        String rawFromId = GsonHelper.getAsString(config, "from_id", null);
        if (rawFromId == null || rawFromId.isBlank()) {
            throw new IllegalArgumentException("Derived grant is missing config.from_id");
        }
        String rawTargetId = readDerivedPointId(config, targetType);
        if (rawTargetId == null || rawTargetId.isBlank()) {
            throw new IllegalArgumentException("Derived grant is missing config target id");
        }
        String source = GsonHelper.getAsString(config, "source", null);
        boolean preferMinecraftNamespace = "attribute".equals(fromType);
        String fromId = normalizeId(rawFromId, defaultNamespace, preferMinecraftNamespace);
        String targetId = normalizeId(rawTargetId, defaultNamespace, "attribute".equals(targetType));
        return new DerivedGrantRule(targetType, targetId, amount, fromType, fromId, per, source);
    }

    private static String readDerivedPointId(JsonObject config, String type) {
        if (config.has("target_id")) {
            return GsonHelper.getAsString(config, "target_id");
        }
        return switch (type) {
            case "attribute" -> GsonHelper.getAsString(config, "target_attribute", null);
            case "skill" -> GsonHelper.getAsString(config, "target_skill_name", null);
            default -> GsonHelper.getAsString(config, "target_key", null);
        };
    }

    public record DerivedGrantRule(
            String targetType,
            String targetId,
            long amount,
            String fromType,
            String fromId,
            long per,
            String source
    ) {
    }

    private record GrantReadResult(
            List<EffectDefinition.Grant> grants,
            List<DerivedGrantRule> derivedGrantRules
    ) {
    }

    private record LoadedEffect(
            EffectDefinition definition,
            List<DerivedGrantRule> derivedGrantRules
    ) {
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
        String type = normalizeEventType(GsonHelper.getAsString(eventObj, "type"));
        JsonObject config = readConfigObject(eventObj);
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
        boolean hidden = GsonHelper.getAsBoolean(eventObj, "hidden", false);
        String customDisplayKey = GsonHelper.getAsString(eventObj, "custom_display_key", null);
        JsonObject extra = collectExtra(eventObj, EVENT_RULE_KEYS);
        return new EffectDefinition.EventRule(type, config, distance, source, item, itemTag, block, blockTag, foodOnly, addPoints, consumePoints, actions, hidden, customDisplayKey, extra);
    }

    private static String normalizeEventType(String rawType) {
        return switch (rawType) {
            case "受到攻击时" -> "attacked";
            case "损失生命时" -> "health_loss";
            case "击杀生物时" -> "kill";
            case "on_biome_change" -> "biome_change";
            case "on_dimension_change" -> "dimension_change";
            default -> rawType;
        };
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
            Integer durationTicks = mutationObj.has("duration_ticks") ? GsonHelper.getAsInt(mutationObj, "duration_ticks") : null;
            mutations.add(new EffectDefinition.PointMutation(type, id, amount, source, chance, durationTicks));
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
            actions.add(readBonusAction(actionObj, defaultNamespace));
        }
        return actions;
    }

    private static List<EffectDefinition.BonusAction> readExecutions(JsonObject effectObj, String defaultNamespace) {
        if (!effectObj.has("executions")) {
            return List.of();
        }
        JsonArray executionArray = GsonHelper.getAsJsonArray(effectObj, "executions");
        List<EffectDefinition.BonusAction> executions = new ArrayList<>();
        for (JsonElement executionElement : executionArray) {
            if (!executionElement.isJsonObject()) {
                continue;
            }
            JsonObject executionObj = executionElement.getAsJsonObject();
            executions.add(readBonusAction(executionObj, defaultNamespace));
        }
        return executions;
    }

    private static EffectDefinition.BonusAction readBonusAction(JsonObject actionObj, String defaultNamespace) {
        String type = GsonHelper.getAsString(actionObj, "type");
        Double amount = actionObj.has("amount") ? GsonHelper.getAsDouble(actionObj, "amount") : null;
        JsonObject config = readConfigObject(actionObj);
        String pointType = GsonHelper.getAsString(actionObj, "point_type", "counter");
        String pointId = actionObj.has("point_id") || actionObj.has("id") || actionObj.has("attribute") || actionObj.has("skill_name") || actionObj.has("key")
                ? readPointId(actionObj, pointType, defaultNamespace, "point_id")
                : null;
        String source = GsonHelper.getAsString(actionObj, "source", null);
        long maxConsume = GsonHelper.getAsLong(actionObj, "max_consume", Long.MAX_VALUE);
        boolean consumePoints = GsonHelper.getAsBoolean(actionObj, "consume_points", false);
        boolean hidden = GsonHelper.getAsBoolean(actionObj, "hidden", false);
        String customDisplayKey = GsonHelper.getAsString(actionObj, "custom_display_key", null);
        String effectId = actionObj.has("effect") ? normalizeId(GsonHelper.getAsString(actionObj, "effect"), defaultNamespace, true) : null;
        Integer durationTicks = actionObj.has("duration_ticks") ? GsonHelper.getAsInt(actionObj, "duration_ticks") : null;
        Integer amplifier = actionObj.has("amplifier") ? GsonHelper.getAsInt(actionObj, "amplifier") : null;
        String target = GsonHelper.getAsString(actionObj, "target", "self");
        List<EffectDefinition.ItemEntry> items = readItemEntries(actionObj, defaultNamespace);
        int rolls = GsonHelper.getAsInt(actionObj, "rolls", 1);
        boolean unique = GsonHelper.getAsBoolean(actionObj, "unique", false);
        boolean dropIfFull = GsonHelper.getAsBoolean(actionObj, "drop_if_full", true);
        EffectDefinition.ChanceConfig chance = readChanceConfig(actionObj);
        JsonObject extra = collectExtra(actionObj, BONUS_ACTION_KEYS);
        return new EffectDefinition.BonusAction(
                type,
                amount,
                config,
                pointId != null ? pointType : null,
                pointId,
                source,
                maxConsume,
                consumePoints,
                hidden,
                customDisplayKey,
                effectId,
                durationTicks,
                amplifier,
                target,
                items,
                rolls,
                unique,
                dropIfFull,
                chance,
                extra
        );
    }

    private static List<EffectDefinition.ItemEntry> readItemEntries(JsonObject actionObj, String defaultNamespace) {
        if (!actionObj.has("items")) {
            return List.of();
        }
        JsonArray itemsArray = GsonHelper.getAsJsonArray(actionObj, "items");
        List<EffectDefinition.ItemEntry> items = new ArrayList<>();
        for (JsonElement itemElement : itemsArray) {
            if (!itemElement.isJsonObject()) {
                continue;
            }
            JsonObject itemObj = itemElement.getAsJsonObject();
            String itemId = normalizeId(GsonHelper.getAsString(itemObj, "item"), defaultNamespace, false);
            int count = GsonHelper.getAsInt(itemObj, "count", 1);
            int weight = Math.max(1, GsonHelper.getAsInt(itemObj, "weight", 1));
            items.add(new EffectDefinition.ItemEntry(itemId, count, weight));
        }
        return items;
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

    private static JsonObject collectExtra(JsonObject source, Set<String> knownKeys) {
        JsonObject extra = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            if (!knownKeys.contains(entry.getKey())) {
                extra.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        return extra;
    }

    private static String normalizeId(String rawId, String defaultNamespace, boolean preferMinecraftNamespace) {
        if (rawId.indexOf(':') >= 0) {
            return rawId;
        }
        String namespace = preferMinecraftNamespace ? "minecraft" : defaultNamespace;
        return ResourceLocation.fromNamespaceAndPath(namespace, rawId).toString();
    }
}
