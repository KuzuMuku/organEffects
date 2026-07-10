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
import cn.kuzuanpa.organeffectprocessor.api.extension.ConditionHandler;
import cn.kuzuanpa.organeffectprocessor.api.extension.OepExtensionApi;
import cn.kuzuanpa.organeffectprocessor.api.extension.PointProducer;
import cn.kuzuanpa.organeffectprocessor.api.extension.RecomputeCallback;
import cn.kuzuanpa.organeffectprocessor.common.capability.EffectCapabilities;
import cn.kuzuanpa.organeffectprocessor.common.capability.EffectPointMap;
import cn.kuzuanpa.organeffectprocessor.common.capability.IEffectHolder;
import cn.kuzuanpa.organeffectprocessor.common.data.OrganEffectData;
import cn.kuzuanpa.organeffectprocessor.common.data.PointConfigData;
import cn.kuzuanpa.organeffectprocessor.common.debug.OepDebug;
import cn.kuzuanpa.organeffectprocessor.common.network.OepNetwork;
import cn.kuzuanpa.organeffectprocessor.common.skill.SkillManager;
import cn.kuzuanpa.organeffectprocessor.common.sync.AttributeSyncer;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public final class EffectRecalculationService {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String ORGAN_SOURCE = "organ";
    public static final String ORGAN_INSTANCE_SOURCE_PREFIX = "organ-instance:";
    public static final String ORGAN_STATIC_INSTANCE_SOURCE_PREFIX = "organ-static-instance:";
    private static final long DAY_TICKS = 24000L;

    private EffectRecalculationService() {
    }

    public static Map<String, Long> recompute(Entity entity) {
        IEffectHolder holder = entity.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder == null) {
            return Map.of();
        }

        Map<String, Long> oldPoints = new LinkedHashMap<>(holder.getEffectPoints());
        EvaluationContext context = EvaluationContext.create(entity);
        // Static effect-instance sources are fully recomputed each pass.
        // Event-earned organ-instance sources (for example source:self from runtime events)
        // deliberately use a different prefix and must survive recompute until consumed/cleared.
        holder.clearSourcesWithPrefix(ORGAN_STATIC_INSTANCE_SOURCE_PREFIX);
        computeEffects(context, holder);

        computeExtensionPoints(entity, holder, context);
        if (entity instanceof Player player) {
            RuntimePointExecutor.execute(player);
        }
        Map<String, Long> newPoints = new LinkedHashMap<>(holder.getEffectPoints());

        if (entity instanceof Player player) {
            applyPlayerEffects(player, oldPoints, newPoints);
        } else if (entity instanceof LivingEntity livingEntity) {
            OrganStatService.applyNonPlayer(livingEntity, newPoints);
        }
        runRecomputeCallbacks(entity, context, oldPoints, newPoints);
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

    public static long dayTicks() {
        return DAY_TICKS;
    }

    public static boolean compareLong(long actual, String operator, Long expected) {
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

    private static void applyPlayerEffects(Player player, Map<String, Long> oldPoints, Map<String, Long> newPoints) {
        AttributeSyncer.applyFromMap(player, oldPoints, newPoints);
        OrganStatService.apply(player, newPoints);
        SkillManager.updatePlayerSkills(player, newPoints);
        if (player instanceof ServerPlayer serverPlayer) {
            OepNetwork.syncSkills(serverPlayer);
            OepNetwork.syncClientPoints(serverPlayer, PointConfigData.INSTANCE.collectClientSyncPoints(newPoints));
        }
    }

    private static void computeEffects(EvaluationContext context, IEffectHolder holder) {
        for (OrganPosition pos : context.positions()) {
            ResourceLocation organId = context.organId(pos);
            if (organId == null) {
                continue;
            }

            int effectIndex = 0;
            for (EffectDefinition effect : OrganEffectData.INSTANCE.getEffectsForOrgan(organId)) {
                String source = ORGAN_STATIC_INSTANCE_SOURCE_PREFIX
                        + organId + "@" + pos.bodyPartId() + "#" + pos.slotIndex() + "/effect/" + effectIndex;
                if (evaluateConditions(context, pos, effect.conditions())) {
                    EffectPointMap pointMap = new EffectPointMap();
                    for (EffectDefinition.Grant grant : effect.grants()) {
                        pointMap.add(grant.type() + ":" + grant.id(), grant.amount());
                    }
                    holder.replaceSourcePoints(source, pointMap.snapshot());
                } else {
                    holder.replaceSourcePoints(source, Map.of());
                }
                effectIndex++;
            }
        }
    }

    private static void computeExtensionPoints(Entity entity, IEffectHolder holder, EvaluationContext context) {
        PointProducer.PointProductionContext productionContext = new PointProducer.PointProductionContext(entity, context);
        for (PointProducer producer : OepExtensionApi.getPointProducers()) {
            EffectPointMap producerPoints = new EffectPointMap();
            producer.producePoints(productionContext, (pointType, pointId, amount) -> producerPoints.add(pointType + ":" + pointId, amount));
            holder.replaceSourcePoints(producer.id(), producerPoints.snapshot());
        }
    }

    private static void runRecomputeCallbacks(Entity entity, EvaluationContext context, Map<String, Long> oldPoints, Map<String, Long> newPoints) {
        RecomputeCallback.RecomputeContext callbackContext = new RecomputeCallback.RecomputeContext(
                entity,
                context,
                Map.copyOf(oldPoints),
                Map.copyOf(newPoints)
        );
        for (RecomputeCallback callback : OepExtensionApi.getRecomputeCallbacks()) {
            try {
                callback.afterRecompute(callbackContext);
            } catch (Exception exception) {
                LOGGER.error("Recompute callback failed for entity {}", entity.getStringUUID(), exception);
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
        ConditionHandler handler = OepExtensionApi.getConditionHandler(condition.type());
        if (handler == null) {
            if (context.entity() instanceof Player player) {
                OepDebug.trace(player, "condition missing type=%s", condition.type());
            }
            return false;
        }
        return handler.test(new ConditionHandler.ConditionContext(context, pos), condition);
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

        public BlockPos blockPos() {
            return entity.blockPosition();
        }

        public Holder<Biome> biome() {
            return entity.level().getBiome(blockPos());
        }

        public BlockState steppedOnBlock() {
            Level level = entity.level();
            BlockPos belowPos = blockPos().below();
            return level.isLoaded(belowPos) ? level.getBlockState(belowPos) : null;
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
