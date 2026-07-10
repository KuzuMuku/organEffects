package cn.kuzuanpa.organeffects.api.extension;

import net.minecraft.world.entity.player.Player;

@FunctionalInterface
public interface SkillExecutor {
    boolean cast(Player player, int level);
}
