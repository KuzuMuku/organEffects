package cn.kuzuanpa.organeffectprocessor.common.skill;

import java.util.List;

public record SkillDefinition(
        String id,
        String nameKey,
        String descriptionKey,
        List<String> description,
        int cooldownTicks,
        int maxLevel
) {
    public SkillDefinition {
        description = List.copyOf(description);
    }
}
