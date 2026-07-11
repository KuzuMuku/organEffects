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

package cn.kuzuanpa.organeffects.common.item;

import cn.kuzuanpa.organapi.common.item.OrganItem;
import net.minecraft.resources.ResourceLocation;

/**
 * Organ item for Organ Effects. Delegates to the base {@link OrganItem}
 * and references the organ definition under {@code organapi/organs/<name>.json}.
 */
public class OrganEffectsOrganItem extends OrganItem {
    public OrganEffectsOrganItem(Properties properties, String definitionName) {
        super(properties, ResourceLocation.fromNamespaceAndPath("organeffects", definitionName));
    }
}
