package dev.mcbookshelf.sniffer.client.config

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

/**
 * Hands the config screen of the mod to ModMenu.
 *
 * @author theogiraudet
 */
@Environment(EnvType.CLIENT)
class ModMenuIntegration : ModMenuApi {

    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
        ConfigScreenFactory { parent -> DebuggerClothConfig.createConfigScreen(parent) }
}
