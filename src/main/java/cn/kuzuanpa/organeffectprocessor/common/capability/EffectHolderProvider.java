package cn.kuzuanpa.organeffectprocessor.common.capability;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

public class EffectHolderProvider implements ICapabilitySerializable<net.minecraft.nbt.CompoundTag> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("organeffectprocessor", "effect_holder");
    private static final String PERSISTENT_DATA_KEY = "organeffectprocessor.effect_holder";

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
        tag.put("points", holder.serializeNBT());
        if (holder.selectedSkillId != null && !holder.selectedSkillId.isBlank()) {
            tag.putString("selected_skill", holder.selectedSkillId);
        }
        saveToOwner(tag);
        return tag;
    }

    @Override
    public void deserializeNBT(net.minecraft.nbt.CompoundTag nbt) {
        holder.deserializeNBT(nbt.getCompound("points"));
        holder.selectedSkillId = nbt.getString("selected_skill");
        saveToOwner(nbt);
    }

    private void loadFromOwner() {
        net.minecraft.nbt.CompoundTag persistentData = owner.getPersistentData();
        if (persistentData.contains(PERSISTENT_DATA_KEY)) {
            net.minecraft.nbt.CompoundTag saved = persistentData.getCompound(PERSISTENT_DATA_KEY);
            holder.deserializeNBT(saved.getCompound("points"));
            holder.selectedSkillId = saved.getString("selected_skill");
        }
    }

    private void saveToOwner(net.minecraft.nbt.CompoundTag tag) {
        owner.getPersistentData().put(PERSISTENT_DATA_KEY, tag.copy());
    }

    private static class EffectPointMapHolder implements IEffectHolder {
        private final Map<String, Long> points = new HashMap<>();
        private boolean dirty;
        private String selectedSkillId = "";

        @Override
        public Map<String, Long> getEffectPoints() {
            return new HashMap<>(points);
        }

        @Override
        public void setEffectPoints(Map<String, Long> newPoints) {
            points.clear();
            points.putAll(newPoints);
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

        net.minecraft.nbt.CompoundTag serializeNBT() {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            for (Map.Entry<String, Long> entry : points.entrySet()) {
                tag.putLong(entry.getKey(), entry.getValue());
            }
            return tag;
        }

        void deserializeNBT(net.minecraft.nbt.CompoundTag nbt) {
            points.clear();
            for (String key : nbt.getAllKeys()) {
                points.put(key, nbt.getLong(key));
            }
            dirty = false;
        }
    }
}
