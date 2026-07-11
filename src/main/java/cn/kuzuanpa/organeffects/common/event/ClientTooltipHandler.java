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

package cn.kuzuanpa.organeffects.common.event;

import cn.kuzuanpa.organapi.api.organ.OrganDefinition;
import cn.kuzuanpa.organapi.common.data.OrganRegistryAccess;
import cn.kuzuanpa.organeffects.common.data.OrganEffectData;
import cn.kuzuanpa.organeffects.common.point.OrganEffectDisplayBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ClientTooltipHandler {
    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        OrganDefinition definition = OrganRegistryAccess.getOrgan(event.getItemStack()).orElse(null);
        if (definition == null) {
            return;
        }
        event.getToolTip().addAll(OrganEffectDisplayBuilder.buildTooltipLines(
                definition,
                OrganEffectData.INSTANCE.getEffectsForOrgan(definition.id()),
                Screen.hasShiftDown()
        ));
    }
}
