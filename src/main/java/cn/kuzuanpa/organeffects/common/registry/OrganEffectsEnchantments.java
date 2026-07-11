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

package cn.kuzuanpa.organeffects.common.registry;

import cn.kuzuanpa.organeffects.OrganEffectsMod;
import cn.kuzuanpa.organeffects.common.enchantment.TensionEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class OrganEffectsEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, OrganEffectsMod.MOD_ID);

    public static final RegistryObject<Enchantment> TENSION = ENCHANTMENTS.register("tension", TensionEnchantment::new);

    private OrganEffectsEnchantments() {
    }
}
