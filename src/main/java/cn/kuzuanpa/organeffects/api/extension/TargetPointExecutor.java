/*
 * This class was created by <kuzuanpa>. It is distributed as
 * part of the organEffects Mod. Get the Source Code in github:
 * https://github.com/KuzuMuku/organEffects
 *
 * organEffects is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.

 * organEffects is Open Source and distributed under the
 * AGPLv3 License: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 */

package cn.kuzuanpa.organeffects.api.extension;

import cn.kuzuanpa.organeffects.api.EffectDefinition;
import cn.kuzuanpa.organeffects.common.capability.IEffectHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** executes when player attacks something**/
public interface TargetPointExecutor {
    String type();

    void execute(TargetExecutionContext context, EffectDefinition.BonusAction action);

    record TargetExecutionContext(Player player, LivingEntity target, OrganEffectsRuntimeEvent event, IEffectHolder holder,
                                  PointUsageResolver usageResolver) {
        public PointExecutor.PointUsage resolveUsage(EffectDefinition.BonusAction action) {
            return usageResolver.resolve(action);
        }
    }

    @FunctionalInterface
    interface PointUsageResolver {
        PointExecutor.PointUsage resolve(EffectDefinition.BonusAction action);
    }
}
