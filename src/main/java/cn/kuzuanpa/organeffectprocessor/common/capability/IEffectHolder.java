package cn.kuzuanpa.organeffectprocessor.common.capability;

import java.util.Map;

public interface IEffectHolder {
    Map<String, Long> getEffectPoints();

    void setEffectPoints(Map<String, Long> points);

    String getSelectedSkillId();

    void setSelectedSkillId(String skillId);

    void markDirty();

    boolean isDirty();

    void clearDirty();
}
