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
        if (holder.selectedSkillId != null && !holder.selectedSkillId.isBlank()) {
            tag.putString("selected_skill", holder.selectedSkillId);
        }
        saveToOwner(tag);
        return tag;
    }

    @Override
    public void deserializeNBT(net.minecraft.nbt.CompoundTag nbt) {
        holder.deserializeAllNBT(nbt);
        holder.selectedSkillId = nbt.getString("selected_skill");
        saveToOwner(nbt);
    }

    private void loadFromOwner() {
        net.minecraft.nbt.CompoundTag persistentData = owner.getPersistentData();
        if (persistentData.contains(PERSISTENT_DATA_KEY)) {
            net.minecraft.nbt.CompoundTag saved = persistentData.getCompound(PERSISTENT_DATA_KEY);
            holder.deserializeAllNBT(saved);
            holder.selectedSkillId = saved.getString("selected_skill");
        }
    }

    private void saveToOwner(net.minecraft.nbt.CompoundTag tag) {
        owner.getPersistentData().put(PERSISTENT_DATA_KEY, tag.copy());
    }

    private static class EffectPointMapHolder implements IEffectHolder {
        private final Map<String, Map<String, Long>> sources = new LinkedHashMap<>();
        private boolean dirty;
        private String selectedSkillId = "";

        @Override
        public Map<String, Long> getEffectPoints() {
            Map<String, Long> merged = new LinkedHashMap<>();
            for (Map<String, Long> sourcePoints : sources.values()) {
                for (Map.Entry<String, Long> entry : sourcePoints.entrySet()) {
                    merged.merge(entry.getKey(), entry.getValue(), Long::sum);
                }
            }
            return merged;
        }

        @Override
        public Map<String, Map<String, Long>> getPointSources() {
            Map<String, Map<String, Long>> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Long>> entry : sources.entrySet()) {
                copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
            }
            return copy;
        }

        @Override
        public Map<String, Long> getPointsForSource(String sourceTag) {
            return new LinkedHashMap<>(sources.getOrDefault(sourceTag, Map.of()));
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
        public String getSelectedSkillId() {
            return selectedSkillId;
        }

        @Override
        public void setSelectedSkillId(String skillId) {
            selectedSkillId = skillId == null ? "" : skillId;
            dirty = true;
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

        void deserializeAllNBT(net.minecraft.nbt.CompoundTag nbt) {
            sources.clear();
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
            dirty = false;
        }
    }
}
