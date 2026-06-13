package cn.kuzuanpa.organeffectprocessor.common.capability;

import java.util.Map;

public interface IEffectHolder {
    Map<String, Long> getEffectPoints();

    Map<String, Map<String, Long>> getPointSources();

    Map<String, Long> getPointsForSource(String sourceTag);

    void replaceSourcePoints(String sourceTag, Map<String, Long> points);

    long addSourcePoint(String sourceTag, String pointKey, long amount);

    long consumeSourcePoint(String sourceTag, String pointKey, long amount);

    long clearSourcePoint(String sourceTag, String pointKey);

    String getSelectedSkillId();

    void setSelectedSkillId(String skillId);

    void markDirty();

    boolean isDirty();

    void clearDirty();
}
