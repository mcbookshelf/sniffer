package dev.mcbookshelf.sniffer.client.config

import me.shedaniel.clothconfig2.api.ConfigBuilder
import dev.mcbookshelf.sniffer.config.DebuggerConfig
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Cloth Config integration for the Sniffer mod.
 * Provides a GUI interface for configuring the debugger.
 *
 * @author theogiraudet
 */
object DebuggerClothConfig {

    /**
     * Creates a new Cloth Config screen for the Sniffer.
     *
     * @param parent The parent screen
     * @return The config screen
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

        // Reference holders for dynamic address text updates
        var currentPort = config.port
        var currentPath = config.path

        val addressSupplier = {
            val wsAddress = "ws://localhost:$currentPort/$currentPath"
            Component.translatable("sniffer.config.server_address", wsAddress)
        }

        // Server address description
        mainCategory.addEntry(
            entryBuilder.startTextDescription(addressSupplier())
                .setTooltip(Component.translatable("sniffer.config.server_address.tooltip"))
                .build()
        )

        // Port entry
        mainCategory.addEntry(
            entryBuilder.startIntField(
                Component.translatable("sniffer.config.port"),
                config.port
            )
                .setDefaultValue(25599)
                .setTooltip(Component.translatable("sniffer.config.port.tooltip"))
                .setMin(1024)
                .setMax(65535)
                .setSaveConsumer { value ->
                    config.port = value
                    currentPort = value
                }
                .build()
        )

        // Path entry
        mainCategory.addEntry(
            entryBuilder.startStrField(
                Component.translatable("sniffer.config.path"),
                config.path
            )
                .setDefaultValue("dap")
                .setTooltip(Component.translatable("sniffer.config.path.tooltip"))
                .setSaveConsumer { value ->
                    config.path = value
                    currentPath = value
                }
                .build()
        )

        return builder.build()
    }
}
