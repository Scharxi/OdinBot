package me.scharxidev.odin.extensions.moderation

import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.supplier.EntitySupplyStrategy
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import mu.KotlinLogging

class ClearCommand : Extension() {
    override val name: String
        get() = "clearcommand"

    override suspend fun setup() {
        val logger = KotlinLogging.logger { }
        ephemeralSlashCommand(::ClearArgs) {
            name = "clear"
            description = "Clears messages"

            check { hasPermission(Permission.ManageMessages) }

            action {
                val messageAmount = arguments.messages
                val textChannel = channel as GuildMessageChannelBehavior

                val messagesToClear = channel.withStrategy(EntitySupplyStrategy.rest)
                    .getMessagesBefore(Snowflake.max, Integer.min(messageAmount, 100))
                    .filterNotNull()
                    .map {
                        it.id
                    }.catch {
                        it.printStackTrace()
                        logger.error("Error in the clear command")
                    }.toList()

                val messages: Int = messagesToClear.size

                textChannel.bulkDelete(messagesToClear, "Clear Command")

                respond {
                    content = "Cleared **$messages messages** :broom:"
                }
            }
        }
    }

    inner class ClearArgs : Arguments() {
        val messages by int {
            name = "messages"
            description = "Number of messages to delete"
        }
    }
}