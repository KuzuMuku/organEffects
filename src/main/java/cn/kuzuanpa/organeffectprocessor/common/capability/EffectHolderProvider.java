package cn.kuzuanpa.organeffectprocessor.common.capability;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

public class EffectHolderProvider implements ICapabilitySerializable<net.minecraft.nbt.CompoundTag> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("organeffectprocessor", "effect_holder");
    private static final String PERSISTENT_DATA_KEY = "organeffectprocessor.effect_holder";
    private static final String LEGACY_POINTS_KEY = "points";
    private static final String SOURCES_KEY = "sources";
    private static final String RUNTIME_POINTS_KEY = "runtime_points";
    private static final String RUNTIME_EXPIRATIONS_KEY = "runtime_expirations";
    private static final String SKILL_COOLDOWNS_KEY = "skill_cooldowns";
    private static final String DEBUG_ENABLED_KEY = "debug_enabled";
    private static final String ORGAN_SOURCE = "organ";

    private final Entity owner;
    private final EffectPointMapHolder holder;
    private final LazyOptional<IEffectHolder> optional;

    public EffectHolderProvider(Entity owner) {
        this.owner = owner;
        this.holder = new EffectPointMapHolder();
        this.optional = LazyOptional.of(() -> holder);
        loadFromOwner();
    }

    @Override
    public <T> LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap, net.minecraft.core.Direction side) {
        return cap == EffectCapabilities.EFFECT_HOLDER ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public net.minecraft.nbt.CompoundTag serializeNBT() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.put(SOURCES_KEY, holder.serializeSourcesNBT());
        tag.put(RUNTIME_POINTS_KEY, holder.serializeRuntimePointsNBT());
        tag.put(RUNTIME_EXPIRATIONS_KEY, holder.serializeRuntimeExpirationsNBT());
        tag.put(SKILL_COOLDOWNS_KEY, holder.serializeSkillCooldownsNBT());
        if (holder.selectedSkillId != null && !holder.selectedSkillId.isBlank()) {
            tag.putString("selected_skill", holder.selectedSkillId);
        }
        tag.putBoolean(DEBUG_ENABLED_KEY, holder.debugEnabled);
        saveToOwner(tag);
        return tag;
    }

    @Override
    public void deserializeNBT(net.minecraft.nbt.CompoundTag nbt) {
        holder.deserializeAllNBT(nbt);
        holder.selectedSkillId = nbt.getString("selected_skill");
        holder.debugEnabled = nbt.getBoolean(DEBUG_ENABLED_KEY);
        saveToOwner(nbt);
    }

    private void loadFromOwner() {
        net.minecraft.nbt.CompoundTag persistentData = owner.getPersistentData();
        if (persistentData.contains(PERSISTENT_DATA_KEY)) {
            net.minecraft.nbt.CompoundTag saved = persistentData.getCompound(PERSISTENT_DATA_KEY);
            holder.deserializeAllNBT(saved);
            holder.selectedSkillId = saved.getString("selected_skill");
            holder.debugEnabled = saved.getBoolean(DEBUG_ENABLED_KEY);
        }
    }

    private void saveToOwner(net.minecraft.nbt.CompoundTag tag) {
        owner.getPersistentData().put(PERSISTENT_DATA_KEY, tag.copy());
    }

    private static class EffectPointMapHolder implements IEffectHolder {
        private final Map<String, Map<String, Long>> sources = new LinkedHashMap<>();
        private final Map<String, Long> runtimePoints = new LinkedHashMap<>();
        private final Map<String, Long> runtimeExpirations = new LinkedHashMap<>();
        private final Map<String, Long> skillCooldowns = new LinkedHashMap<>();
        private boolean dirty;
        private boolean debugEnabled;
        private String selectedSkillId = "";

        @Override
        public Map<String, Long> getEffectPoints() {
            Map<String, Long> merged = new LinkedHashMap<>(getStaticPoints());
            for (Map.Entry<String, Long> entry : runtimePoints.entrySet()) {
                merged.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
            return merged;
        }

        @Override
        public Map<String, Long> getStaticPoints() {
            Map<String, Long> merged = new LinkedHashMap<>();
            for (Map<String, Long> sourcePoints : sources.values()) {
                for (Map.Entry<String, Long> entry : sourcePoints.entrySet()) {
                    merged.merge(entry.getKey(), entry.getValue(), Long::sum);
                }
            }
            return merged;
        }

        @Override
        public Map<String, Long> getRuntimePoints() {
            return new LinkedHashMap<>(runtimePoints);
        }

        @Override
        public Map<String, Map<String, Long>> getPointSources() {
            Map<String, Map<String, Long>> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Long>> entry : sources.entrySet()) {
                copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
            }
            if (!runtimePoints.isEmpty()) {
                copy.put("runtime", new LinkedHashMap<>(runtimePoints));
            }
            return copy;
        }

        @Override
        public Map<String, Long> getPointsForSource(String sourceTag) {
            if ("runtime".equals(sourceTag)) {
                return new LinkedHashMap<>(runtimePoints);
            }
            return new LinkedHashMap<>(sources.getOrDefault(sourceTag, Map.of()));
        }

        @Override
        public long getPooledSourcePoints(String pointKey) {
            return getPooledSourcePoints(pointKey, null);
        }

        @Override
        public long getPooledSourcePoints(String pointKey, String sourceTag) {
            if (pointKey == null || pointKey.isBlank()) {
                return 0L;
            }
            if (sourceTag != null && !sourceTag.isBlank() && !"self".equals(sourceTag)) {
                return sources.getOrDefault(sourceTag, Map.of()).getOrDefault(pointKey, 0L);
            }
            long total = 0L;
            for (Map<String, Long> sourcePoints : sources.values()) {
                total += sourcePoints.getOrDefault(pointKey, 0L);
            }
            return total;
        }

        @Override
        public long consumePooledSourcePoints(String pointKey, long amount) {
            return consumePooledSourcePoints(pointKey, null, amount);
        }

        @Override
        public long consumePooledSourcePoints(String pointKey, String sourceTag, long amount) {
            if (pointKey == null || pointKey.isBlank() || amount <= 0L) {
                return 0L;
            }
            if (sourceTag != null && !sourceTag.isBlank() && !"self".equals(sourceTag)) {
                return consumeSourcePoint(sourceTag, pointKey, amount);
            }
            long remaining = amount;
            long consumed = 0L;
            for (String currentSource : new LinkedHashMap<>(sources).keySet()) {
                if (remaining <= 0L) {
                    break;
                }
                long used = consumeSourcePoint(currentSource, pointKey, remaining);
                if (used <= 0L) {
                    continue;
                }
                consumed += used;
                remaining -= used;
            }
            return consumed;
        }

        @Override
        public void replaceSourcePoints(String sourceTag, Map<String, Long> points) {
            Map<String, Long> cleaned = new LinkedHashMap<>();
            for (Map.Entry<String, Long> entry : points.entrySet()) {
                if (entry.getValue() != null && entry.getValue() != 0L) {
                    cleaned.put(entry.getKey(), entry.getValue());
                }
            }
            if (cleaned.isEmpty()) {
                sources.remove(sourceTag);
            } else {
                sources.put(sourceTag, cleaned);
            }
            dirty = true;
        }

        @Override
        public long addSourcePoint(String sourceTag, String pointKey, long amount) {
            if (amount == 0L) {
                return getPointsForSource(sourceTag).getOrDefault(pointKey, 0L);
            }
            Map<String, Long> sourcePoints = sources.computeIfAbsent(sourceTag, key -> new LinkedHashMap<>());
            long value = sourcePoints.getOrDefault(pointKey, 0L) + amount;
            if (value == 0L) {
                sourcePoints.remove(pointKey);
            } else {
                sourcePoints.put(pointKey, value);
            }
            if (sourcePoints.isEmpty()) {
                sources.remove(sourceTag);
            }
            dirty = true;
            return value;
        }

        @Override
        public long consumeSourcePoint(String sourceTag, String pointKey, long amount) {
            if (amount <= 0L) {
                return 0L;
            }
            Map<String, Long> sourcePoints = sources.get(sourceTag);
            if (sourcePoints == null) {
                return 0L;
            }
            long current = sourcePoints.getOrDefault(pointKey, 0L);
            long consumed = Math.min(current, amount);
            long remaining = current - consumed;
            if (remaining == 0L) {
                sourcePoints.remove(pointKey);
            } else {
                sourcePoints.put(pointKey, remaining);
            }
            if (sourcePoints.isEmpty()) {
                sources.remove(sourceTag);
            }
            if (consumed > 0L) {
                dirty = true;
            }
            return consumed;
        }

        @Override
        public long clearSourcePoint(String sourceTag, String pointKey) {
            Map<String, Long> sourcePoints = sources.get(sourceTag);
            if (sourcePoints == null) {
                return 0L;
            }
            long removed = sourcePoints.getOrDefault(pointKey, 0L);
            sourcePoints.remove(pointKey);
            if (sourcePoints.isEmpty()) {
                sources.remove(sourceTag);
            }
            if (removed != 0L) {
                dirty = true;
            }
            return removed;
        }

        @Override
        public int clearSourcesWithPrefix(String prefix) {
            if (prefix == null || prefix.isBlank()) {
                return 0;
            }
            int removed = 0;
            for (String sourceTag : new LinkedHashMap<>(sources).keySet()) {
                if (sourceTag.startsWith(prefix)) {
                    sources.remove(sourceTag);
                    removed++;
                }
            }
            if (removed > 0) {
                dirty = true;
            }
            return removed;
        }

        @Override
        public long addRuntimePoint(String pointKey, long amount, long expireAtTick) {
            if (amount == 0L) {
                return runtimePoints.getOrDefault(pointKey, 0L);
            }
            long value = runtimePoints.getOrDefault(pointKey, 0L) + amount;
            if (value == 0L) {
                runtimePoints.remove(pointKey);
                runtimeExpirations.remove(pointKey);
            } else {
                runtimePoints.put(pointKey, value);
                if (expireAtTick > 0L) {
                    runtimeExpirations.merge(pointKey, expireAtTick, Math::max);
                }
            }
            dirty = true;
            return value;
        }

        @Override
        public long consumeRuntimePoint(String pointKey, long amount) {
            if (amount <= 0L) {
                return 0L;
            }
            long current = runtimePoints.getOrDefault(pointKey, 0L);
            long consumed = Math.min(current, amount);
            long remaining = current - consumed;
            if (remaining == 0L) {
                runtimePoints.remove(pointKey);
                runtimeExpirations.remove(pointKey);
            } else {
                runtimePoints.put(pointKey, remaining);
            }
            if (consumed > 0L) {
                dirty = true;
            }
            return consumed;
        }

        @Override
        public long clearRuntimePoint(String pointKey) {
            long removed = runtimePoints.getOrDefault(pointKey, 0L);
            runtimePoints.remove(pointKey);
            runtimeExpirations.remove(pointKey);
            if (removed != 0L) {
                dirty = true;
            }
            return removed;
        }

        @Override
        public void clearExpiredRuntimePoints(long gameTime) {
            boolean changed = false;
            for (String pointKey : new LinkedHashMap<>(runtimeExpirations).keySet()) {
                long expireAt = runtimeExpirations.getOrDefault(pointKey, 0L);
                if (expireAt > 0L && gameTime >= expireAt) {
                    runtimeExpirations.remove(pointKey);
                    Long removed = runtimePoints.remove(pointKey);
                    if (removed != null) {
                        changed = true;
                    }
                }
            }
            if (changed) {
                dirty = true;
            }
        }

        @Override
        public Map<String, Long> getRuntimeExpirations() {
            return new LinkedHashMap<>(runtimeExpirations);
        }

        @Override
        public boolean isDebugEnabled() {
            return debugEnabled;
        }

        @Override
        public void setDebugEnabled(boolean debugEnabled) {
            this.debugEnabled = debugEnabled;
            dirty = true;
        }

        @Override
        public String getSelectedSkillId() {
            return selectedSkillId;
        }

        @Override
        public void setSelectedSkillId(String skillId) {
            selectedSkillId = skillId == null ? "" : skillId;
            dirty = true;
        }

        @Override
        public Map<String, Long> getSkillCooldowns() {
            return new LinkedHashMap<>(skillCooldowns);
        }

        @Override
        public long getSkillCooldownExpiration(String skillId) {
            return skillCooldowns.getOrDefault(skillId, 0L);
        }

        @Override
        public void setSkillCooldownExpiration(String skillId, long expireAtTick) {
            if (skillId == null || skillId.isBlank()) {
                return;
            }
            if (expireAtTick <= 0L) {
                if (skillCooldowns.remove(skillId) != null) {
                    dirty = true;
                }
                return;
            }
            Long previous = skillCooldowns.put(skillId, expireAtTick);
            if (previous == null || previous.longValue() != expireAtTick) {
                dirty = true;
            }
        }

        @Override
        public void clearExpiredSkillCooldowns(long gameTime) {
            boolean changed = false;
            for (String skillId : new LinkedHashMap<>(skillCooldowns).keySet()) {
                long expireAt = skillCooldowns.getOrDefault(skillId, 0L);
                if (expireAt > 0L && gameTime >= expireAt) {
                    skillCooldowns.remove(skillId);
                    changed = true;
                }
            }
            if (changed) {
                dirty = true;
            }
        }

        @Override
        public void markDirty() {
            dirty = true;
        }

        @Override
        public boolean isDirty() {
            return dirty;
        }

        @Override
        public void clearDirty() {
            dirty = false;
        }

        net.minecraft.nbt.CompoundTag serializeSourcesNBT() {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            for (Map.Entry<String, Map<String, Long>> sourceEntry : sources.entrySet()) {
                net.minecraft.nbt.CompoundTag sourceTag = new net.minecraft.nbt.CompoundTag();
                for (Map.Entry<String, Long> entry : sourceEntry.getValue().entrySet()) {
                    sourceTag.putLong(entry.getKey(), entry.getValue());
                }
                tag.put(sourceEntry.getKey(), sourceTag);
            }
            return tag;
        }

        net.minecraft.nbt.CompoundTag serializeRuntimePointsNBT() {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            for (Map.Entry<String, Long> entry : runtimePoints.entrySet()) {
                tag.putLong(entry.getKey(), entry.getValue());
            }
            return tag;
        }

        net.minecraft.nbt.CompoundTag serializeRuntimeExpirationsNBT() {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            for (Map.Entry<String, Long> entry : runtimeExpirations.entrySet()) {
                tag.putLong(entry.getKey(), entry.getValue());
            }
            return tag;
        }

        net.minecraft.nbt.CompoundTag serializeSkillCooldownsNBT() {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            for (Map.Entry<String, Long> entry : skillCooldowns.entrySet()) {
                tag.putLong(entry.getKey(), entry.getValue());
            }
            return tag;
        }

        void deserializeAllNBT(net.minecraft.nbt.CompoundTag nbt) {
            sources.clear();
            runtimePoints.clear();
            runtimeExpirations.clear();
            skillCooldowns.clear();
            if (nbt.contains(SOURCES_KEY)) {
                net.minecraft.nbt.CompoundTag sourceRoot = nbt.getCompound(SOURCES_KEY);
                for (String sourceKey : sourceRoot.getAllKeys()) {
                    net.minecraft.nbt.CompoundTag sourceTag = sourceRoot.getCompound(sourceKey);
                    Map<String, Long> sourcePoints = new LinkedHashMap<>();
                    for (String pointKey : sourceTag.getAllKeys()) {
                        sourcePoints.put(pointKey, sourceTag.getLong(pointKey));
                    }
                    if (!sourcePoints.isEmpty()) {
                        sources.put(sourceKey, sourcePoints);
                    }
                }
            } else if (nbt.contains(LEGACY_POINTS_KEY)) {
                net.minecraft.nbt.CompoundTag legacyPoints = nbt.getCompound(LEGACY_POINTS_KEY);
                Map<String, Long> migrated = new HashMap<>();
                for (String key : legacyPoints.getAllKeys()) {
                    migrated.put(key, legacyPoints.getLong(key));
                }
                if (!migrated.isEmpty()) {
                    sources.put(ORGAN_SOURCE, migrated);
                }
            }
            if (nbt.contains(RUNTIME_POINTS_KEY)) {
                net.minecraft.nbt.CompoundTag runtimeRoot = nbt.getCompound(RUNTIME_POINTS_KEY);
                for (String pointKey : runtimeRoot.getAllKeys()) {
                    runtimePoints.put(pointKey, runtimeRoot.getLong(pointKey));
                }
            }
            if (nbt.contains(RUNTIME_EXPIRATIONS_KEY)) {
                net.minecraft.nbt.CompoundTag expirationRoot = nbt.getCompound(RUNTIME_EXPIRATIONS_KEY);
                for (String pointKey : expirationRoot.getAllKeys()) {
                    runtimeExpirations.put(pointKey, expirationRoot.getLong(pointKey));
                }
            }
            if (nbt.contains(SKILL_COOLDOWNS_KEY)) {
                net.minecraft.nbt.CompoundTag cooldownRoot = nbt.getCompound(SKILL_COOLDOWNS_KEY);
                for (String skillId : cooldownRoot.getAllKeys()) {
                    skillCooldowns.put(skillId, cooldownRoot.getLong(skillId));
                }
            }
            dirty = false;
        }
    }
}
