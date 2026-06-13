package cn.kuzuanpa.organeffectprocessor.common.data;

/**
 * Removed – organ effects are already loaded by {@link OrganEffectData} which
 * reads the {@code effects[]} array directly from organ JSON files under
 * {@code organapi/organs/}. Keeping this class empty avoids breaking existing
 * references; the {@code AddReloadListenerEvent} registration in the main mod
 * class points to a no-op singleton.
 */
public final class EffectDefinitionLoader {
    private EffectDefinitionLoader() {
    }
}
