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

import cn.kuzuanpa.organapi.api.query.OrganPosition;
import cn.kuzuanpa.organeffects.api.EffectDefinition;
import cn.kuzuanpa.organeffects.common.effect.EffectRecalculationService;

@FunctionalInterface
public interface ConditionHandler {
    boolean test(ConditionContext context, EffectDefinition.Condition condition);

    record ConditionContext(
            EffectRecalculationService.EvaluationContext evaluationContext,
            OrganPosition position
    ) {
    }
}
