package cn.kuzuanpa.organeffectprocessor.common.capability;

import java.util.Map;

public interface IEffectHolder {
    Map<String, Long> getEffectPoints();

    Map<String, Long> getStaticPoints();

    Map<String, Long> getRuntimePoints();

    Map<String, Map<String, Long>> getPointSources();

    Map<String, Long> getPointsForSource(String sourceTag);

    void replaceSourcePoints(String sourceTag, Map<String, Long> points);

    long addSourcePoint(String sourceTag, String pointKey, long amount);

    long consumeSourcePoint(String sourceTag, String pointKey, long amount);

    long clearSourcePoint(String sourceTag, String pointKey);

    int clearSourcesWithPrefix(String prefix);

    long addRuntimePoint(String pointKey, long amount, long expireAtTick);

    long consumeRuntimePoint(String pointKey, long amount);

    long clearRuntimePoint(String pointKey);

    void clearExpiredRuntimePoints(long gameTime);

    Map<String, Long> getRuntimeExpirations();

    boolean isDebugEnabled();

    void setDebugEnabled(boolean debugEnabled);

    String getSelectedSkillId();

    void setSelectedSkillId(String skillId);

    void markDirty();

    boolean isDirty();

    void clearDirty();
}
