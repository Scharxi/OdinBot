package me.scharxidev.odin.extensions

import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import java.lang.Integer.min

class Moderation : Extension() {
    override val name: String = "moderation"

    override suspend fun setup() {
        val logger = KotlinLogging.logger {}

        ephemeralSlashCommand(::ClearArgs) {
            name = "clear"
            description = "Clears messages"

            check { hasPermission(Permission.ManageMessages) }

            action {
                val messageAmount = arguments.messages
                val messageHolder = mutableListOf<Snowflake>()
                val textChannel = channel as GuildMessageChannelBehavior

                channel.getMessagesAround(channel.messages.last().id, min(messageAmount, 100))

                channel.getMessagesBefore(channel.messages.last().id, min(messageAmount, 100))
                    .filterNotNull()
                    .onEach {
                        messageHolder.add(it.fetchMessage().id)
                    }.catch {
                        it.printStackTrace()
                        logger.error("Error in the clear command")
                    }.collect()

                textChannel.bulkDelete(messageHolder, "Clear Command")

                respond {
                    content = "Messages cleared :broom:"
                }
                //TODO: Send embed in action log
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