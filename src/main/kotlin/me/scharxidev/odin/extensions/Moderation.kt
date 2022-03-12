package me.scharxidev.odin.extensions

import com.kotlindiscord.kord.extensions.DISCORD_GREEN
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalInt
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.getOrThrow
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.behavior.channel.edit
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.supplier.EntitySupplyStrategy
import kotlinx.coroutines.flow.*
import me.scharxidev.odin.database.DatabaseHelper
import me.scharxidev.odin.database.DatabaseManager
import me.scharxidev.odin.util.ResponseHelper
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
        ephemeralSlashCommand {
            name = "slowmo"
            description = "Change the rate limit of the user for all users"

            ephemeralSubCommand(::SlowMode) {
                name = "set"
                description = "Set the rate limit"

                check { hasPermission(Permission.ManageChannels) }

                action {
                    val seconds = if (arguments.seconds > 21600) 21600 else arguments.seconds
                    val textChannel = channel as GuildMessageChannelBehavior
                    val lastRateLimit = textChannel.asChannel().data.rateLimitPerUser.getOrThrow()

                    (textChannel.asChannel() as TextChannel).edit {
                        rateLimitPerUser = seconds
                    }

                    respond {
                        content =
                            "Changed the rate limit of the channel from $lastRateLimit seconds to $seconds seconds"
                    }

                    val actionLogId = DatabaseHelper.selectFromConfig(guild!!.id, DatabaseManager.Config.modActionLog)

                    if (actionLogId.isEmpty()) {
                        respond {
                            content =
                                "**Error:** Unable to access config for this guild. Make sure, that your config is set."
                        }
                        return@action
                    }

                    val actionLog = guild?.getChannel(Snowflake(actionLogId.orNull()!!)) as GuildMessageChannelBehavior
                    ResponseHelper.responseEmbedInChannel(
                        actionLog,
                        "Changed rate limit",
                        "Changed rate limit of ${channel.asChannel().mention} from **${lastRateLimit} seconds** to **${seconds} seconds**.",
                        DISCORD_GREEN,
                        user.asUser()
                    )
                }
            }


            ephemeralSubCommand {
                name = "reset"
                description = "Resets the rate limit"

                check { hasPermission(Permission.ManageChannels) }

                action {
                    val textChannel = channel as GuildMessageChannelBehavior
                    val lastRateLimit = textChannel.asChannel().data.rateLimitPerUser.getOrThrow()
                    (textChannel.asChannel() as TextChannel).edit {
                        rateLimitPerUser = 0
                    }

                    respond {
                        content = "Successfully reset the rate limit"
                    }

                    val actionLogId = DatabaseHelper.selectFromConfig(guild!!.id, DatabaseManager.Config.modActionLog)

                    if (actionLogId.isEmpty()) {
                        respond {
                            content =
                                "**Error:** Unable to access config for this guild. Make sure, that your config is set."
                        }
                        return@action
                    }

                    val actionLog = guild?.getChannel(Snowflake(actionLogId.orNull()!!)) as GuildMessageChannelBehavior
                    ResponseHelper.responseEmbedInChannel(
                        actionLog,
                        "Reset rate limit",
                        "Changed rate limit of ${channel.asChannel().mention} from **${lastRateLimit} seconds** to **0 seconds**.",
                        DISCORD_GREEN,
                        user.asUser()
                    )
                }
            }
        }

        ephemeralSlashCommand {
            name = "nuke"
            description = "Nukes the channel"

            check { hasPermission(Permission.ManageChannels) }

            action {
                val textChannel: GuildMessageChannelBehavior = channel as GuildMessageChannelBehavior

                val messagesToDelete = channel.withStrategy(EntitySupplyStrategy.rest).getMessagesBefore(Snowflake.max, 100)
                    .filterNotNull()
                    .map { it.id }
                    .toList()

                try {
                    textChannel.bulkDelete(messagesToDelete, "Channel Nuke")
                } catch (e: Exception) {
                    respond {
                        content = "Could not nuke channel."
                    }
                }

                respond {
                    content = "Successfully nuked channel!"
                }

                val actionLogId = DatabaseHelper.selectFromConfig(guild!!.id, DatabaseManager.Config.modActionLog)

                if (actionLogId.isEmpty()) {
                    respond {
                        content =
                            "**Error:** Unable to access config for this guild. Make sure, that your config is set."
                    }
                    return@action
                }

                val actionLog = guild?.getChannel(Snowflake(actionLogId.orNull()!!)) as GuildMessageChannelBehavior
                ResponseHelper.responseEmbedInChannel(
                    actionLog,
                    "Purge was successful",
                    "Successfully purged ${textChannel.mention}",
                    DISCORD_GREEN,
                    user.asUser()
                )
            }
        }

        ephemeralSlashCommand(::PurgeArgs) {
            name = "purge"
            description = "Deletes the messages of an user"

            check { hasPermission(Permission.ManageMessages) }

            action {
                val textChannel = channel as GuildMessageChannelBehavior
                val user = arguments.user
                val amount = arguments.messages

                val messagesToDelete = channel.withStrategy(EntitySupplyStrategy.rest).getMessagesBefore(Snowflake.max, amount)
                    .filterNotNull()
                    .filter { it.author?.id == user.id }
                    .map { it.id }
                    .toList()

                val messages = messagesToDelete.size

                try {
                    textChannel.bulkDelete(messagesToDelete, "Purge of user ${user.username}")
                } catch (e: Exception) {
                    respond {
                        content = "Could not purge channel."
                    }
                }

                respond {
                    content = "Purged **${messages} messages** of **${user.username}**"
                }
            }
        }


    }

    inner class PurgeArgs : Arguments() {
        val user by user {
            name = "user"
            description = "The user whose messages you want to delete"
        }
        val messages by optionalInt {
            name = "messages"
            description = "The amount of messages you want to delete"
        }
    }

    inner class ClearArgs : Arguments() {
        val messages by int {
            name = "messages"
            description = "Number of messages to delete"
        }
    }

    inner class SlowMode : Arguments() {
        val seconds by int {
            name = "seconds"
            description = "Number of seconds to limit the users rate limit"
        }
    }
}