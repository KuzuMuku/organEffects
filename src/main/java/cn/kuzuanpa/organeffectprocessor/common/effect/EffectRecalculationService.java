package cn.kuzuanpa.organeffectprocessor.common.effect;

import cn.kuzuanpa.organapi.api.body.BodyPartDefinition;
import cn.kuzuanpa.organapi.api.body.BodyPartIds;
import cn.kuzuanpa.organapi.api.body.ResolvedBodyPlan;
import cn.kuzuanpa.organapi.api.organ.OrganDefinition;
import cn.kuzuanpa.organapi.api.query.OrganPosition;
import cn.kuzuanpa.organapi.api.query.OrganQueryService;
import cn.kuzuanpa.organapi.common.body.BodyPlanResolver;
import cn.kuzuanpa.organapi.common.data.OrganRegistryAccess;
import cn.kuzuanpa.organeffectprocessor.api.EffectDefinition;
import cn.kuzuanpa.organeffectprocessor.common.capability.EffectCapabilities;
import cn.kuzuanpa.organeffectprocessor.common.capability.EffectPointMap;
import cn.kuzuanpa.organeffectprocessor.common.capability.IEffectHolder;
import cn.kuzuanpa.organeffectprocessor.common.data.OrganEffectData;
import cn.kuzuanpa.organeffectprocessor.common.network.OepNetwork;
import cn.kuzuanpa.organeffectprocessor.common.skill.SkillManager;
import cn.kuzuanpa.organeffectprocessor.common.sync.AttributeSyncer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class EffectRecalculationService {
    public static final String ORGAN_SOURCE = "organ";
    private static final long DAY_TICKS = 24000L;

    private EffectRecalculationService() {
    }

    public static Map<String, Long> recompute(Entity entity) {
        IEffectHolder holder = entity.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder == null) {
            return Map.of();
        }

        EffectPointMap pointMap = new EffectPointMap();
        computeEffects(entity, pointMap);

        Map<String, Long> oldPoints = holder.getEffectPoints();
        holder.replaceSourcePoints(ORGAN_SOURCE, pointMap.snapshot());
        Map<String, Long> newPoints = holder.getEffectPoints();

        if (entity instanceof Player player) {
            applyPlayerEffects(player, oldPoints, newPoints);
        }
        return newPoints;
    }

    public static void reapply(Player player) {
        IEffectHolder holder = player.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder == null) {
            return;
        }
        Map<String, Long> points = holder.getEffectPoints();
        applyPlayerEffects(player, Map.of(), points);
    }

    private static void applyPlayerEffects(Player player, Map<String, Long> oldPoints, Map<String, Long> newPoints) {
        AttributeSyncer.applyFromMap(player, oldPoints, newPoints);
        SkillManager.updatePlayerSkills(player, newPoints);
        if (player instanceof ServerPlayer serverPlayer) {
            OepNetwork.syncSkills(serverPlayer);
        }
    }

    private static void computeEffects(Entity entity, EffectPointMap target) {
        target.clear();
        EvaluationContext context = EvaluationContext.create(entity);
        for (OrganPosition pos : context.positions()) {
            ResourceLocation organId = context.organId(pos);
            if (organId == null) {
                continue;
            }

            for (EffectDefinition effect : OrganEffectData.INSTANCE.getEffectsForOrgan(organId)) {
                if (evaluateConditions(context, pos, effect.conditions())) {
                    for (EffectDefinition.Grant grant : effect.grants()) {
                        target.add(grant.type() + ":" + grant.id(), grant.amount());
                    }
                }
            }
        }
    }

    public static boolean evaluateConditions(Entity entity, OrganPosition pos, List<EffectDefinition.Condition> conditions) {
        return evaluateConditions(EvaluationContext.create(entity), pos, conditions);
    }

    public static boolean evaluateConditions(EvaluationContext context, OrganPosition pos, List<EffectDefinition.Condition> conditions) {
        for (EffectDefinition.Condition condition : conditions) {
            if (!evaluateCondition(context, pos, condition)) {
                return false;
            }
        }
        return true;
    }

    private static boolean evaluateCondition(EvaluationContext context, OrganPosition pos, EffectDefinition.Condition condition) {
        return switch (condition.type()) {
            case "static" -> true;
            case "slot_index" -> compareLong(pos.slotIndex(), condition.operator(), condition.value());
            case "distance_to_edge" -> compareLong(context.distanceToEdge(pos, condition.edge()), condition.operator(), condition.value());
            case "weather" -> matchesWeather(context, condition.weather());
            case "time" -> matchesTime(context, condition);
            case "has_organ" -> matchesOrganLink(context, pos, condition);
            default -> false;
        };
    }

    private static boolean matchesWeather(EvaluationContext context, String weather) {
        if (weather == null) {
            return false;
        }
        return switch (weather) {
            case "clear" -> !context.entity().level().isRaining() && !context.entity().level().isThundering();
            case "rain" -> context.entity().level().isRaining() && !context.entity().level().isThundering();
            case "thunder" -> context.entity().level().isThundering();
            default -> false;
        };
    }

    private static boolean matchesTime(EvaluationContext context, EffectDefinition.Condition condition) {
        if (condition.time() != null) {
            return switch (condition.time()) {
                case "day" -> context.entity().level().isDay();
                case "night" -> !context.entity().level().isDay();
                default -> false;
            };
        }

        long timeOfDay = Math.floorMod(context.entity().level().getDayTime(), DAY_TICKS);
        if (condition.min() != null || condition.max() != null) {
            long min = condition.min() != null ? condition.min() : 0L;
            long max = condition.max() != null ? condition.max() : DAY_TICKS - 1L;
            if (min <= max) {
                return timeOfDay >= min && timeOfDay <= max;
            }
            return timeOfDay >= min || timeOfDay <= max;
        }
        return compareLong(timeOfDay, condition.operator(), condition.value());
    }

    private static boolean matchesOrganLink(EvaluationContext context, OrganPosition pos, EffectDefinition.Condition condition) {
        if (condition.scope() == null || condition.organ() == null) {
            return false;
        }
        ResourceLocation organId = ResourceLocation.tryParse(condition.organ());
        if (organId == null) {
            return false;
        }

        return switch (condition.scope()) {
            case "whole_body" -> context.organCount(organId) > 0;
            case "body_part" -> {
                ResourceLocation bodyPartId = ResourceLocation.tryParse(condition.bodyPart());
                yield bodyPartId != null && context.hasOrganInBodyPart(bodyPartId, organId);
            }
            case "exact_position" -> {
                ResourceLocation bodyPartId = condition.bodyPart() != null ? ResourceLocation.tryParse(condition.bodyPart()) : pos.bodyPartId();
                Integer slot = condition.slot();
                yield bodyPartId != null && slot != null && organId.equals(context.organAt(bodyPartId, slot));
            }
            case "symmetric_position" -> context.symmetricBodyPart(pos.bodyPartId())
                    .map(bodyPartId -> organId.equals(context.organAt(bodyPartId, pos.slotIndex())))
                    .orElse(false);
            default -> false;
        };
    }

    private static boolean compareLong(long actual, String operator, Long expected) {
        if (operator == null || expected == null) {
            return false;
        }
        return switch (operator) {
            case "eq" -> actual == expected;
            case "ne" -> actual != expected;
            case "gt" -> actual > expected;
            case "gte" -> actual >= expected;
            case "lt" -> actual < expected;
            case "lte" -> actual <= expected;
            default -> false;
        };
    }

    public record EvaluationContext(
            Entity entity,
            ResolvedBodyPlan bodyPlan,
            List<OrganPosition> positions,
            Map<ResourceLocation, Integer> organCounts,
            Map<ResourceLocation, Map<Integer, ResourceLocation>> organsByPartAndSlot,
            Map<ResourceLocation, SlotGrid> gridsByPart,
            Map<OrganPosition, ResourceLocation> organIdsByPosition
    ) {
        public static EvaluationContext create(Entity entity) {
            ResolvedBodyPlan bodyPlan = BodyPlanResolver.resolve(entity);
            List<OrganPosition> positions = OrganQueryService.getInstalledOrganPositions(entity);
            Map<ResourceLocation, Integer> organCounts = new HashMap<>();
            Map<ResourceLocation, Map<Integer, ResourceLocation>> organsByPartAndSlot = new HashMap<>();
            Map<ResourceLocation, SlotGrid> gridsByPart = new HashMap<>();
            Map<OrganPosition, ResourceLocation> organIdsByPosition = new HashMap<>();

            for (ResourceLocation bodyPartId : bodyPlan.getOrderedBodyPartIds()) {
                int capacity = OrganQueryService.getTotalCapacity(entity, bodyPartId);
                BodyPartDefinition definition = bodyPlan.getBodyPart(bodyPartId)
                        .orElse(BodyPartDefinition.simple(bodyPartId, Math.max(1, capacity), 0));
                gridsByPart.put(bodyPartId, SlotGrid.of(capacity, definition));
            }

            for (OrganPosition position : positions) {
                ResourceLocation organId = OrganRegistryAccess.getOrgan(position.organ())
                        .map(OrganDefinition::id)
                        .orElse(null);
                if (organId == null) {
                    continue;
                }
                organIdsByPosition.put(position, organId);
                organCounts.merge(organId, 1, Integer::sum);
                organsByPartAndSlot.computeIfAbsent(position.bodyPartId(), key -> new HashMap<>())
                        .put(position.slotIndex(), organId);
            }

            return new EvaluationContext(entity, bodyPlan, positions, organCounts, organsByPartAndSlot, gridsByPart, organIdsByPosition);
        }

        public ResourceLocation organId(OrganPosition position) {
            return organIdsByPosition.get(position);
        }

        public int organCount(ResourceLocation organId) {
            return organCounts.getOrDefault(organId, 0);
        }

        public boolean hasOrganInBodyPart(ResourceLocation bodyPartId, ResourceLocation organId) {
            Map<Integer, ResourceLocation> slotMap = organsByPartAndSlot.get(bodyPartId);
            if (slotMap == null) {
                return false;
            }
            return slotMap.containsValue(organId);
        }

        public ResourceLocation organAt(ResourceLocation bodyPartId, int slot) {
            return organsByPartAndSlot.getOrDefault(bodyPartId, Map.of()).get(slot);
        }

        public Optional<ResourceLocation> symmetricBodyPart(ResourceLocation bodyPartId) {
            if (BodyPartIds.LEFT_ARM.equals(bodyPartId)) {
                return Optional.of(BodyPartIds.RIGHT_ARM);
            }
            if (BodyPartIds.RIGHT_ARM.equals(bodyPartId)) {
                return Optional.of(BodyPartIds.LEFT_ARM);
            }
            if (BodyPartIds.LEFT_LEG.equals(bodyPartId)) {
                return Optional.of(BodyPartIds.RIGHT_LEG);
            }
            if (BodyPartIds.RIGHT_LEG.equals(bodyPartId)) {
                return Optional.of(BodyPartIds.LEFT_LEG);
            }
            return Optional.empty();
        }

        public long distanceToEdge(OrganPosition position, String edge) {
            SlotGrid grid = gridsByPart.get(position.bodyPartId());
            if (grid == null) {
                return Long.MAX_VALUE;
            }
            int row = position.slotIndex() / grid.columns();
            int column = position.slotIndex() % grid.columns();
            return switch (edge) {
                case "top" -> row;
                case "bottom" -> grid.rows() - 1L - row;
                case "left" -> column;
                case "right" -> grid.columns() - 1L - column;
                default -> Long.MAX_VALUE;
            };
        }
    }

    public record SlotGrid(int columns, int rows) {
        public static SlotGrid of(int capacity, BodyPartDefinition definition) {
            int safeCapacity = Math.max(1, capacity);
            float base = (float) Math.sqrt(safeCapacity);
            float widthBias = Math.max(0.4F, definition.visualWidthRatio());
            float heightBias = Math.max(0.4F, definition.visualHeightRatio());
            int columns = Math.max(1, Math.round(base * (float) Math.sqrt(widthBias / heightBias)));
            columns = Math.min(columns, Math.max(1, safeCapacity));
            int rows = (int) Math.ceil((double) safeCapacity / columns);
            while (columns > 1 && (rows - 1) * columns >= safeCapacity) {
                rows--;
            }
            return new SlotGrid(columns, rows);
        }
    }
}
