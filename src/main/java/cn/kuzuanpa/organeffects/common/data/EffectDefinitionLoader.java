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

package cn.kuzuanpa.organeffects.common.data;

/**
 * Removed – organ effects are already loaded by {@link OrganEffectData} which
 * reads the {@code effects[]} array directly from organ JSON files under
 * {@code organapi/organs/}. Keeping this class empty avoids breaking existing
 * references; the {@code AddReloadListenerEvent} registration in the main mod
 * class points to a no-op singleton.
 */
public final class EffectDefinitionLoader {
    private EffectDefinitionLoader() {
    }
}
