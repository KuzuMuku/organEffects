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

package cn.kuzuanpa.organeffects.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

public record EffectDefinition(
        List<Condition> conditions,
        List<Grant> grants,
        List<EventRule> events,
        List<BonusAction> executions
) {
    public EffectDefinition {
        conditions = List.copyOf(conditions);
        grants = List.copyOf(grants);
        events = List.copyOf(events);
        executions = List.copyOf(executions);
    }

    public record Condition(
            String type,
            JsonObject config
    ) {
        public Condition {
            config = config == null ? new JsonObject() : config.deepCopy();
        }

        public JsonElement configValue(String key) {
            return config.get(key);
        }

        public String configString(String key) {
            JsonElement value = configValue(key);
            return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
        }

        public Long configLong(String key) {
            JsonElement value = configValue(key);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsLong() : null;
        }

        public Double configDouble(String key) {
            JsonElement value = configValue(key);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsDouble() : null;
        }

        public Boolean configBoolean(String key) {
            JsonElement value = configValue(key);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : null;
        }
    }

    public record Grant(
            String type,
            String id,
            long amount,
            boolean hidden
    ) {
    }

    public record EventRule(
            String type,
            JsonObject config,
            Long distance,
            String source,
            String item,
            String itemTag,
            String block,
            String blockTag,
            boolean foodOnly,
            List<PointMutation> addPoints,
            List<PointMutation> consumePoints,
            List<BonusAction> actions,
            boolean hidden,
            String customDisplayKey,
            JsonObject extra
    ) {
        public EventRule {
            config = config == null ? new JsonObject() : config.deepCopy();
            addPoints = List.copyOf(addPoints);
            consumePoints = List.copyOf(consumePoints);
            actions = List.copyOf(actions);
            extra = extra == null ? new JsonObject() : extra.deepCopy();
        }

        public JsonElement configValue(String key) {
            return config.get(key);
        }

        public String configString(String key) {
            JsonElement value = configValue(key);
            return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
        }

        public Long configLong(String key) {
            JsonElement value = configValue(key);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsLong() : null;
        }

        public Double configDouble(String key) {
            JsonElement value = configValue(key);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsDouble() : null;
        }

        public Boolean configBoolean(String key) {
            JsonElement value = configValue(key);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : null;
        }

        public JsonElement extraValue(String key) {
            return extra.get(key);
        }

        public String extraString(String key) {
            JsonElement value = extraValue(key);
            return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
        }

        public Long extraLong(String key) {
            JsonElement value = extraValue(key);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsLong() : null;
        }

        public Double extraDouble(String key) {
            JsonElement value = extraValue(key);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsDouble() : null;
        }

        public Boolean extraBoolean(String key) {
            JsonElement value = extraValue(key);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : null;
        }
    }

    public record PointMutation(
            String type,
            String id,
            long amount,
            String source,
            ChanceConfig chance,
            Integer durationTicks
    ) {
    }

    public record ChanceConfig(
            Double base,
            Double luckyStep,
            Double max
    ) {
    }

    public record BonusAction(
            String type,
            Double amount,
            JsonObject config,
            String pointType,
            String pointId,
            String source,
            long maxConsume,
            boolean isPointsConsume,
            boolean hidden,
            String customDisplayKey,
            String effectId,
            Integer durationTicks,
            Integer amplifier,
            String target,
            List<ItemEntry> items,
            int rolls,
            boolean unique,
            boolean dropIfFull,
            ChanceConfig chance,
            JsonObject extra
    ) {
        public BonusAction {
            config = config == null ? new JsonObject() : config.deepCopy();
            items = items == null ? List.of() : List.copyOf(items);
            extra = extra == null ? new JsonObject() : extra.deepCopy();
        }

        public JsonElement configValue(String key) {
            return config.get(key);
        }

        public String configString(String key) {
            JsonElement value = configValue(key);
            return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
        }

        public Long configLong(String key) {
            JsonElement value = configValue(key);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsLong() : null;
        }

        public Double configDouble(String key) {
            JsonElement value = configValue(key);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsDouble() : null;
        }

        public Boolean configBoolean(String key) {
            JsonElement value = configValue(key);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : null;
        }

        public JsonElement extraValue(String key) {
            return extra.get(key);
        }

        public String extraString(String key) {
            JsonElement value = extraValue(key);
            return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
        }

        public Long extraLong(String key) {
            JsonElement value = extraValue(key);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsLong() : null;
        }

        public Double extraDouble(String key) {
            JsonElement value = extraValue(key);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsDouble() : null;
        }

        public Boolean extraBoolean(String key) {
            JsonElement value = extraValue(key);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : null;
        }
    }

    public record ItemEntry(
            String itemId,
            int count,
            int weight
    ) {
    }
}
