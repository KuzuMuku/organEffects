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

package cn.kuzuanpa.organeffects.client.input;

import cn.kuzuanpa.organeffects.OrganEffectsMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = OrganEffectsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class OrganEffectsKeyMappings {
    public static final KeyMapping SKILL_KEY = new KeyMapping(
            "key.organeffects.skill",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            "category.organeffects.skills"
    );

    private OrganEffectsKeyMappings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SKILL_KEY);
    }
}
