package dev.mcbookshelf.sniffer.client.config

import me.shedaniel.clothconfig2.api.ConfigBuilder
import dev.mcbookshelf.sniffer.config.DebuggerConfig
import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Builds the Cloth Config screen the mod settings are edited from.
 *
 * @author theogiraudet
 */
object DebuggerClothConfig {

    /**
     * @param parent the screen to go back to when the config screen closes
     */
    @JvmStatic
    fun createConfigScreen(parent: Screen): Screen {
        val config = DebuggerConfig.getInstance()

        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("sniffer.config.title"))
            .setSavingRunnable(config::save)

        val entryBuilder = builder.entryBuilder()

        val mainCategory = builder.getOrCreateCategory(
            Component.translatable("sniffer.config.category.main")
        )

        if (Minecraft.getInstance().currentServer != null) {
            mainCategory.addEntry(
                entryBuilder.startTextDescription(Component.translatable("sniffer.config.client_warning"))
                    .setColor(0xFFFFFF55.toInt())
                    .build()
            )
        }

        mainCategory.addEntry(
            entryBuilder.startStrField(
                Component.translatable("sniffer.config.host"),
                config.host
            )
                .setDefaultValue("localhost")
                .setTooltip(Component.translatable("sniffer.config.host.tooltip"))
                .setSaveConsumer { value ->
                    config.host = value
                }
                .build()
        )

        mainCategory.addEntry(
            entryBuilder.startIntField(
                Component.translatable("sniffer.config.port"),
                config.port
            )
                .setDefaultValue(25599)
                .setTooltip(Component.translatable("sniffer.config.port.tooltip"))
                .setMin(1024)
                .setMax(65535)
                .setSaveConsumer { config.port = it }
                .build()
        )

        mainCategory.addEntry(
            entryBuilder.startStrField(
                Component.translatable("sniffer.config.path"),
                config.path
            )
                .setDefaultValue("dap")
                .setTooltip(Component.translatable("sniffer.config.path.tooltip"))
                .setSaveConsumer { config.path = it }
                .build()
        )

        mainCategory.addEntry(
            entryBuilder.startBooleanToggle(
                Component.translatable("sniffer.config.authEnabled"),
                config.authEnabled
            )
                .setDefaultValue(true)
                .setTooltip(Component.translatable("sniffer.config.authEnabled.tooltip"))
                .setSaveConsumer { value -> config.authEnabled = value }
                .build()
        )

        mainCategory.addEntry(
            entryBuilder.startIntField(
                Component.translatable("sniffer.config.authPromptTimeoutSeconds"),
                config.authPromptTimeoutSeconds
            )
                .setDefaultValue(30)
                .setTooltip(Component.translatable("sniffer.config.authPromptTimeoutSeconds.tooltip"))
                .setMin(1)
                .setMax(600)
                .setSaveConsumer { value -> config.authPromptTimeoutSeconds = value }
                .build()
        )

        return builder.build()
    }
}
