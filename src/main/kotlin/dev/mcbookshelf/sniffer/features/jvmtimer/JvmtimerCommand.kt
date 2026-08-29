package dev.mcbookshelf.sniffer.features.jvmtimer

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.context.CommandContext
import com.mojang.logging.LogUtils
import dev.mcbookshelf.sniffer.util.Extension.appendLine
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.CommonColors
import kotlin.math.max
import kotlin.math.min
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import dev.mcbookshelf.sniffer.command.SnifferCommand

/**
 * The `/jvmtimer` command, which times datapack functions from the JVM side.
 * Its subcommands all work on named timers.
 *
 * @author Alumopper
 */
object JvmtimerCommand : SnifferCommand {

    private val LOGGER = LogUtils.getLogger()

    val timers = HashMap<String, JvmTimer>()

    override fun build(dispatcher: CommandDispatcher<CommandSourceStack>): LiteralArgumentBuilder<CommandSourceStack> =
        literal<CommandSourceStack>("jvmtimer")
            .then(literal<CommandSourceStack>("start")
                .then(argument("id", StringArgumentType.string())
                    .suggests(JvmtimerSuggestionProvider)
                    .executes {
                        val id = StringArgumentType.getString(it, "id")
                        getTimer(id).start()
                        it.source.sendSuccess({ Component.translatable("sniffer.commands.jvmtimer.started", id) }, false)
                        1
                    }
                )
            ).then(literal<CommandSourceStack>("end")
                .then(argument("id", StringArgumentType.string())
                    .suggests(JvmtimerSuggestionProvider)
                    .executes {
                        val id = StringArgumentType.getString(it ,"id")
                        getTimer(id).end()
                        it.source.sendSuccess({ Component.translatable("sniffer.commands.jvmtimer.stopped", id) }, false)
                        1
                    }
                )
            ).then(literal<CommandSourceStack>("get")
                .then(argument("id", StringArgumentType.string())
                    .suggests(JvmtimerSuggestionProvider)
                    .executes {
                        val id = StringArgumentType.getString(it ,"id")
                        getTimer(id).get(it)
                        1
                    }
                )
            ).then(literal<CommandSourceStack>("reset")
                .then(argument("id", StringArgumentType.string())
                    .suggests(JvmtimerSuggestionProvider)
                    .executes {
                        val id = StringArgumentType.getString(it ,"id")
                        getTimer(id).reset()
                        it.source.sendSuccess({ Component.translatable("sniffer.commands.jvmtimer.reset", id) }, false)
                        1
                    }
                )
            ).then(literal<CommandSourceStack>("disable")
                .then(argument("id", StringArgumentType.string())
                    .suggests(JvmtimerSuggestionProvider)
                    .executes {
                        val id = StringArgumentType.getString(it ,"id")
                        getTimer(id).disable()
                        it.source.sendSuccess({ Component.translatable("sniffer.commands.jvmtimer.disable", id) }, false)
                        1
                    }
                )
            )

    fun getTimer(id: String): JvmTimer{
        return timers[id] ?: JvmTimer(id).also { timers[id] = it }
    }

    class JvmTimer(val id: String) {

        private var startTime = -1L

        private var totalTime = 0L

        private var count = 0

        private var enabled = true

        private var minTime = Long.MAX_VALUE

        private var maxTime = Long.MIN_VALUE

        fun start(){
            if(!enabled) return
            if((startTime != -1L)){
                LOGGER.warn("Timer $id is already started! Possible end missing? Timer $id has been disable!")
                enabled = false
                startTime = -1L
            }
            startTime = System.nanoTime()
        }

        fun end(){
            if(!enabled) return
            if((startTime == -1L)){
                LOGGER.warn("Timer $id is not started")
                enabled = false
                return
            }
            val delta = System.nanoTime() - startTime
            totalTime += delta
            minTime = min(minTime, delta)
            maxTime = max(maxTime, delta)
            count++
            startTime = -1L
        }

        fun get(ctx: CommandContext<CommandSourceStack>){
            if(count == 0){
                ctx.source.sendSuccess({ Component.translatable("sniffer.commands.jvmtimer.not_started", id) }, false)
                return
            }
            if(!enabled){
                ctx.source.sendSuccess({ Component.translatable("sniffer.commands.jvmtimer.disable", id) }, false)
                return
            }
            val text = Component.empty()
            text.title("sniffer.commands.jvmtimer.info.id").value(id)
                .desc("sniffer.commands.jvmtimer.info.total").value("${totalTime / 1_000_000.0}ms")
                .desc("sniffer.commands.jvmtimer.info.count").value(count.toString())
                .desc("sniffer.commands.jvmtimer.info.average").value("${totalTime / count / 1_000_000.0}ms")
                .desc("sniffer.commands.jvmtimer.info.max_min").value("${maxTime / 1_000.0}μs/${minTime / 1_000.0}μs")
            ctx.source.sendSuccess({ text }, false)
        }
        fun reset(){
            startTime = -1L
            totalTime = 0L
            count = 0
            enabled = true
            maxTime = Long.MIN_VALUE
            minTime = Long.MAX_VALUE
        }

        fun disable(){
            reset()
            enabled = false
        }

        private fun MutableComponent.title(str: String): MutableComponent =
            this.appendLine(Component.translatable(str).withStyle { it.withColor(CommonColors.HIGH_CONTRAST_DIAMOND).withBold(true) })

        private fun MutableComponent.desc(str: String): MutableComponent =
            this.appendLine(Component.translatable(str).withStyle { it.withColor(CommonColors.WHITE).withBold(false) })

        private fun MutableComponent.value(str: String): MutableComponent =
            this.appendLine(Component.literal(str).withStyle { it.withColor(CommonColors.HIGH_CONTRAST_DIAMOND).withBold(false) })
    }
}