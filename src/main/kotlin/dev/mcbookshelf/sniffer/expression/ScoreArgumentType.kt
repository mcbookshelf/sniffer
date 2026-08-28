package dev.mcbookshelf.sniffer.expression

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.ObjectiveArgument
import net.minecraft.commands.arguments.ScoreHolderArgument
import net.minecraft.nbt.IntTag
import net.minecraft.network.chat.Component

/**
 * Parses `score <holder> <objective>`, yielding the value of that score.
 *
 * @author Alumopper
 */
class ScoreArgumentType: ArgumentType<ScoreArgumentType.Score> {

    @Suppress("unused", "PrivatePropertyName")
    private val EXAMPLES = listOf("{score @s test}", "{score player objective}")
    @Suppress("PrivatePropertyName")
    private val ERROR = SimpleCommandExceptionType { "Invalid score argument" }

    override fun parse(reader: StringReader): Score {
        skipWhitespace(reader)
        val keyword = reader.readUnquotedString()
        if("score" != keyword){
            throw ERROR.createWithContext(reader)
        }
        skipWhitespace(reader)
        val scoreHolder = ScoreHolderArgument.scoreHolder().parse(reader)
        skipWhitespace(reader)
        val objective = ObjectiveArgument.objective().parse(reader)
        return Score(scoreHolder, objective)
    }

    class Score (
        val scoreHolder: ScoreHolderArgument.Result,
        val objective: String
    ): DebugData {
        override fun get(source: CommandSourceStack): Any {
            val scoreboard = source.server.scoreboard
            val holder = scoreHolder.getNames(source) { ArrayList() }.last()
            val scoreboardObjective = scoreboard.getObjective(objective)
                ?: throw DynamicCommandExceptionType {
                    Component.translatable("arguments.objective.notFound", *arrayOf(it))
                }.create(objective)
            val readableScoreboardScore = scoreboard.getOrCreatePlayerScore(holder, scoreboardObjective)
            if (readableScoreboardScore == null) {
                throw PLAYERS_GET_NULL_EXCEPTION.create(
                    scoreboardObjective.name,
                    holder.displayName
                )
            } else {
                return IntTag.valueOf(readableScoreboardScore.get())
            }
        }

    }

    companion object {
        fun score(): ScoreArgumentType = ScoreArgumentType()

        private fun skipWhitespace(reader: StringReader) {
            while (reader.canRead() && Character.isWhitespace(reader.peek())) reader.skip()
        }
        private val PLAYERS_GET_NULL_EXCEPTION = Dynamic2CommandExceptionType { objective: Any?, target: Any? ->
            Component.translatable(
                "commands.scoreboard.players.get.null",
                objective ?: "null",
                target ?: "null"
            )
        }
    }
}
