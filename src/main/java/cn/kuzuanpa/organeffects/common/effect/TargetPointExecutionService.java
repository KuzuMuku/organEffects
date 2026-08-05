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

package cn.kuzuanpa.organeffects.common.effect;

import cn.kuzuanpa.organapi.api.query.OrganPosition;
import cn.kuzuanpa.organapi.api.query.OrganQueryService;
import cn.kuzuanpa.organapi.common.data.OrganRegistryAccess;
import cn.kuzuanpa.organeffects.api.EffectDefinition;
import cn.kuzuanpa.organeffects.api.extension.OrganEffectsExtensionApi;
import cn.kuzuanpa.organeffects.api.extension.OrganEffectsRuntimeEvent;
import cn.kuzuanpa.organeffects.api.extension.TargetPointExecutor;
import cn.kuzuanpa.organeffects.common.capability.IEffectHolder;
import cn.kuzuanpa.organeffects.common.data.OrganEffectData;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public final class TargetPointExecutionService {
    private TargetPointExecutionService() {
    }

    public static void handleEvent(Player player, IEffectHolder holder, OrganEffectsRuntimeEvent event) {
        if (event.target() == null || !event.target().isAlive()) {
            return;
        }
        for (OrganPosition position : OrganQueryService.getInstalledOrganPositions(player)) {
            ResourceLocation organId = OrganRegistryAccess.getOrgan(position.organ()).map(definition -> definition.id()).orElse(null);
            if (organId == null) {
                continue;
            }
            List<EffectDefinition> effects = OrganEffectData.INSTANCE.getEffectsForOrgan(organId);
            for (EffectDefinition effect : effects) {
                if (effect.executions().isEmpty()) {
                    continue;
                }
                if (!EffectRecalculationService.evaluateConditions(player, position, effect.conditions())) {
                    continue;
                }
                for (EffectDefinition.BonusAction action : effect.executions()) {
                    TargetPointExecutor executor = OrganEffectsExtensionApi.getTargetPointExecutor(action.type());
                    if (executor == null || !hasUsablePoints(holder, action)) {
                        continue;
                    }
                    executor.execute(new TargetPointExecutor.TargetExecutionContext(
                            player,
                            event.target(),
                            event,
                            holder,
                            boundAction -> RuntimePointExecutor.previewPointUsage(holder, boundAction)
                    ), action);
                }
            }
        }
    }

    private static boolean hasUsablePoints(IEffectHolder holder, EffectDefinition.BonusAction action) {
        if (action.pointType() == null || action.pointId() == null) {
            return false;
        }
        String pointKey = action.pointType() + ":" + action.pointId();
        long runtimeAvailable = holder.getRuntimePoints().getOrDefault(pointKey, 0L);
        long sourceAvailable = holder.getPooledSourcePoints(pointKey, action.source());
        return runtimeAvailable > 0L || sourceAvailable > 0L;
    }
}
