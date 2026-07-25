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
import cn.kuzuanpa.organeffects.api.EffectDefinition;
import cn.kuzuanpa.organeffects.api.extension.PointProducer;
import cn.kuzuanpa.organeffects.common.capability.EffectCapabilities;
import cn.kuzuanpa.organeffects.common.capability.IEffectHolder;
import cn.kuzuanpa.organeffects.common.data.OrganEffectData;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class DerivedGrantPointProducer implements PointProducer {
    @Override
    public String id() {
        return "organeffects:derived_grants";
    }

    @Override
    public int getPriority() {
        return -100;
    }

    @Override
    public void producePoints(PointProductionContext context, MutablePointSink sink) {
        Entity entity = context.entity();
        IEffectHolder holder = entity.getCapability(EffectCapabilities.EFFECT_HOLDER).orElse(null);
        if (holder == null) {
            return;
        }

        Map<String, Long> staticPoints = holder.getStaticPoints();
        for (OrganPosition pos : context.evaluationContext().positions()) {
            ResourceLocation organId = context.evaluationContext().organId(pos);
            if (organId == null) {
                continue;
            }

            int effectIndex = 0;
            for (EffectDefinition effect : OrganEffectData.INSTANCE.getEffectsForOrgan(organId)) {
                if (!EffectRecalculationService.evaluateConditions(context.evaluationContext(), pos, effect.conditions())) {
                    effectIndex++;
                    continue;
                }
                for (OrganEffectData.DerivedGrantRule rule : OrganEffectData.INSTANCE.getDerivedGrantRulesForEffect(organId, effectIndex)) {
                    long scaledAmount = resolveScaledAmount(holder, staticPoints, rule);
                    if (scaledAmount != 0L) {
                        sink.add(rule.targetType(), rule.targetId(), scaledAmount);
                    }
                }
                effectIndex++;
            }
        }
    }

    private static long resolveScaledAmount(IEffectHolder holder, Map<String, Long> staticPoints, OrganEffectData.DerivedGrantRule rule) {
        String pointKey = rule.fromType() + ":" + rule.fromId();
        long sourcePoints = rule.source() == null || rule.source().isBlank()
                ? staticPoints.getOrDefault(pointKey, 0L)
                : holder.getPooledSourcePoints(pointKey, rule.source());
        if (sourcePoints <= 0L) {
            return 0L;
        }
        return rule.amount() * (sourcePoints / rule.per());
    }
}
