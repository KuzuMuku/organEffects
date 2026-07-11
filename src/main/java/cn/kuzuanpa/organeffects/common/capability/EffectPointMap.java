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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class EffectPointMap {
    private final Map<String, Long> points = new LinkedHashMap<>();

    public void add(String key, long amount) {
        points.merge(key, amount, Long::sum);
        if (points.getOrDefault(key, 0L) == 0L) {
            points.remove(key);
        }
    }

    public void set(String key, long value) {
        if (value == 0L) {
            points.remove(key);
            return;
        }
        points.put(key, value);
    }

    public long get(String key) {
        return points.getOrDefault(key, 0L);
    }

    public void clear() {
        points.clear();
    }

    public Map<String, Long> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(points));
    }
}
