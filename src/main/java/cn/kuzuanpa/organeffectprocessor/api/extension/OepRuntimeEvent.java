package cn.kuzuanpa.organeffectprocessor.api.extension;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public record OepRuntimeEvent(
        String type,
        LivingEntity entity,
        LivingEntity target,
        Entity directEntity,
        ItemStack itemStack,
        BlockState blockState,
        double distanceMoved,
        double amount,
        JsonObject extra
) {
    public OepRuntimeEvent {
        itemStack = itemStack == null ? ItemStack.EMPTY : itemStack.copy();
        extra = extra == null ? new JsonObject() : extra.deepCopy();
    }

    public boolean isProjectileAttack() {
        return directEntity != null && directEntity != entity;
    }

    public JsonElement extraValue(String key) {
        return extra.get(key);
    }

    public String extraString(String key) {
        JsonElement value = extraValue(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    public Double extraDouble(String key) {
        JsonElement value = extraValue(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsDouble() : null;
    }

    public Long extraLong(String key) {
        JsonElement value = extraValue(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsLong() : null;
    }

    public Boolean extraBoolean(String key) {
        JsonElement value = extraValue(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : null;
    }

    public static Builder builder(String type, LivingEntity entity) {
        return new Builder(type, entity);
    }

    public static final class Builder {
        private final String type;
        private final LivingEntity entity;
        private LivingEntity target;
        private Entity directEntity;
        private ItemStack itemStack = ItemStack.EMPTY;
        private BlockState blockState;
        private double distanceMoved;
        private double amount;
        private JsonObject extra;

        private Builder(String type, LivingEntity entity) {
            this.type = type;
            this.entity = entity;
        }

        public Builder target(LivingEntity target) {
            this.target = target;
            return this;
        }

        public Builder directEntity(Entity directEntity) {
            this.directEntity = directEntity;
            return this;
        }

        public Builder itemStack(ItemStack itemStack) {
            this.itemStack = itemStack;
            return this;
        }

        public Builder blockState(BlockState blockState) {
            this.blockState = blockState;
            return this;
        }

        public Builder distanceMoved(double distanceMoved) {
            this.distanceMoved = distanceMoved;
            return this;
        }

        public Builder amount(double amount) {
            this.amount = amount;
            return this;
        }

        public Builder extra(JsonObject extra) {
            this.extra = extra;
            return this;
        }

        public OepRuntimeEvent build() {
            return new OepRuntimeEvent(type, entity, target, directEntity, itemStack, blockState, distanceMoved, amount, extra);
        }
    }
}
