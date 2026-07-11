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

package cn.kuzuanpa.organeffects.common.capability;

import java.util.Map;

public interface IEffectHolder {
    Map<String, Long> getEffectPoints();

    Map<String, Long> getStaticPoints();

    Map<String, Long> getRuntimePoints();

    Map<String, Map<String, Long>> getPointSources();

    Map<String, Long> getPointsForSource(String sourceTag);

    long getPooledSourcePoints(String pointKey);

    long getPooledSourcePoints(String pointKey, String sourceTag);

    long consumePooledSourcePoints(String pointKey, long amount);

    long consumePooledSourcePoints(String pointKey, String sourceTag, long amount);

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

    Map<String, Long> getSkillCooldowns();

    long getSkillCooldownExpiration(String skillId);

    void setSkillCooldownExpiration(String skillId, long expireAtTick);

    void clearExpiredSkillCooldowns(long gameTime);

    void markDirty();

    boolean isDirty();

    void clearDirty();
}
