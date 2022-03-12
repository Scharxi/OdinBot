package me.scharxidev.odin.extensions

import com.kotlindiscord.kord.extensions.DISCORD_BLACK
import com.kotlindiscord.kord.extensions.DISCORD_GREEN
import com.kotlindiscord.kord.extensions.annotations.DoNotChain
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.mute
import com.kotlindiscord.kord.extensions.utils.timeoutUntil
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.getOrThrow
import dev.kord.core.behavior.ban
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.edit
import dev.kord.core.behavior.edit
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.supplier.EntitySupplyStrategy
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import me.scharxidev.odin.database.DatabaseHelper
import me.scharxidev.odin.database.DatabaseManager
import me.scharxidev.odin.util.ResponseHelper
import mu.KotlinLogging
import java.lang.Integer.min

class Moderation : Extension() {
    override val name: String = "moderation"

    @OptIn(DoNotChain::class)
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

                val messagesToDelete =
                    channel.withStrategy(EntitySupplyStrategy.rest).getMessagesBefore(Snowflake.max, 100)
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

                val messagesToDelete =
                    channel.withStrategy(EntitySupplyStrategy.rest).getMessagesBefore(Snowflake.max, amount)
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
                    "Successfully purged **$messages** of **${user.username}** in ${channel.asChannel().mention}",
                    DISCORD_GREEN,
                    user.asUser()
                )
            }
        }

        ephemeralSlashCommand(::SoftBanArgs) {
            name = "soft-ban"
            description = "Soft-bans an user"

            check { hasPermission(Permission.BanMembers) }

            action {
                val actionLogId = DatabaseHelper.selectFromConfig(guild!!.id, DatabaseManager.Config.modActionLog)
                val moderators = DatabaseHelper.selectFromConfig(guild!!.id, DatabaseManager.Config.moderatorPing)

                if (actionLogId.isEmpty() || moderators.isEmpty()) {
                    respond {
                        content = "**Error:** Unable to access config for this guild. Make sure, that your config is set!"
                    }
                    return@action
                }

                val actionLog = guild?.getChannel(Snowflake(actionLogId.orNull()!!)) as GuildMessageChannelBehavior
                val userArg = arguments.user

                try {
                    val roles = userArg.asMember(guild!!.id).roles.toList().map { it.id }

                    if (guild?.getMember(userArg.id)?.isBot == true) {
                        respond {
                            content = "You cannot soft-ban a bot-user!"
                        }
                        return@action
                    } else if (Snowflake(moderators.orNull()!!) in roles) {
                        respond {
                            content = "You cannot soft-ban moderators!"
                        }
                        return@action
                    }
                } catch (e: Exception) {
                    logger.warn("IsBot and Moderator checks skipped on `Soft-Ban` due to error")
                }

                val dm = ResponseHelper.userDmEmbed(
                    userArg,
                    "You have been soft-banned from ${guild?.fetchGuild()?.name}",
                    "**Reason:**\n${arguments.reason}\n\nYou're free to rejoin without the need to be unbanned",
                    null
                )

                guild?.getMember(userArg.id)?.edit { timeoutUntil = null }

                guild?.ban(userArg.id) {
                    reason = "${arguments.reason} + **SOFT-BAN**"
                    deleteMessagesDays = arguments.messages
                }

                respond {
                    content = "Soft-Banned ${userArg.username}"
                }

                actionLog.createEmbed {
                    color = DISCORD_BLACK
                    title = "Soft-banned a user"
                    description = "${userArg.username} has been soft banned\n${userArg.id} (${userArg.tag})"

                    field {
                        name = "Reason:"
                        value = arguments.reason
                        inline = true
                    }
                    field {
                        name = "Days of messages deleted:"
                        value = arguments.messages.toString()
                        inline = false
                    }
                    field {
                        name = "User Notification:"
                        value =
                            if (dm != null) {
                                "User notified with a direct message"
                            } else {
                                "Failed to notify user with a direct message"
                            }
                        inline = false
                    }
                    footer {
                        text = "Requested by ${user.asUser().tag}"
                        icon = user.asUser().avatar?.url
                    }
                    timestamp = Clock.System.now()
                }
                guild?.unban(userArg.id)
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

    inner class SoftBanArgs : Arguments() {
        val user by user {
            name = "user"
            description = "User to soft ban"
        }
        val messages by defaultingInt {
            name = "messages"
            description = "Messages"
            defaultValue = 3
        }
        val reason by defaultingString {
            name = "reason"
            description = "Reason for the soft ban"
            defaultValue = "No reason provided"
        }
    }
}