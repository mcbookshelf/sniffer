package dev.mcbookshelf.sniffer.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

/**
 * Configuration class for the Sniffer mod.
 * Handles loading and saving configuration settings.
 *
 * @author theogiraudet
 */
class DebuggerConfig {

    var port: Int = 25599
        set(value) {
            field = value
            save()
        }

    var path: String = "dap"
        set(value) {
            field = value
            save()
        }

    fun save() {
        val configFile = FabricLoader.getInstance().configDir.resolve(CONFIG_FILENAME).toFile()
        try {
            configFile.parentFile?.mkdirs()
            FileWriter(configFile).use { writer ->
                GSON.toJson(this, writer)
                LOGGER.info("Saved debugger configuration to {}", configFile)
            }
        } catch (e: IOException) {
            LOGGER.error("Failed to save configuration", e)
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("sniffer")
        private const val CONFIG_FILENAME = "sniffer.json"
        private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

        private var instance: DebuggerConfig? = null

        @JvmStatic
        fun getInstance(): DebuggerConfig {
            if (instance == null) {
                instance = load()
            }
            return instance!!
        }

        private fun load(): DebuggerConfig {
            val configFile = FabricLoader.getInstance().configDir.resolve(CONFIG_FILENAME).toFile()

            if (configFile.exists()) {
                try {
                    FileReader(configFile).use { reader ->
                        val config = GSON.fromJson(reader, DebuggerConfig::class.java)
                        LOGGER.info("Loaded debugger configuration from {}", configFile)
                        return config
                    }
                } catch (e: IOException) {
                    LOGGER.error("Failed to load configuration, using defaults", e)
                }
            }

            val config = DebuggerConfig()
            config.save()
            LOGGER.info("Created default configuration at {}", configFile)
            return config
        }
    }
}
