package me.scharxidev.odin.extensions

import com.kotlindiscord.kord.extensions.DISCORD_BLACK
import com.kotlindiscord.kord.extensions.DISCORD_GREEN
import com.kotlindiscord.kord.extensions.annotations.DoNotChain
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.timeoutUntil
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.ban
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.edit
import dev.kord.core.supplier.EntitySupplyStrategy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import me.scharxidev.odin.database.DatabaseHelper
import me.scharxidev.odin.database.DatabaseManager
import me.scharxidev.odin.util.ResponseHelper
import mu.KotlinLogging

open class Moderation : Extension() {
    override val name: String = "moderation"

    @OptIn(DoNotChain::class, kotlin.time.ExperimentalTime::class)
    override suspend fun setup() {
        val logger = KotlinLogging.logger { }

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
                        content =
                            "**Error:** Unable to access config for this guild. Make sure, that your config is set!"
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

        ephemeralSlashCommand(::KickArgs) {
            name = "kick"
            description = "Kicks an user"

            check { hasPermission(Permission.KickMembers) }

            action {
                val reason = arguments.reason
                val userArg = arguments.user

                val actionLogId = DatabaseHelper.selectFromConfig(guild!!.id, DatabaseManager.Config.modActionLog)
                val moderators = DatabaseHelper.selectFromConfig(guild!!.id, DatabaseManager.Config.moderatorPing)
                if (moderators.isEmpty() || actionLogId.isEmpty()) {
                    respond {
                        content =
                            "**Error:** Unable to access config for this guild! Is your configuration set?"
                    }
                    return@action
                }

                val actionLog = guild?.getChannel(Snowflake(actionLogId.orNull()!!)) as GuildMessageChannelBehavior

                try {
                    val roles = userArg.asMember(guild!!.id).roles.toList().map { it.id }

                    if (guild?.getMember(userArg.id)?.isBot == true) {
                        respond {
                            content = "You cannot kick a bot user!"
                        }
                        return@action
                    } else if (Snowflake(moderators.orNull()!!) in roles) {
                        respond {
                            content = "You cannot kick a moderator"
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("IsBot and Moderator checks skipped on `Kick` due to error")
                }

                val dm = ResponseHelper.userDmEmbed(
                    userArg,
                    "You have been kicked from ${guild?.fetchGuild()?.name}",
                    "**Reason:**\n${arguments.reason}",
                    null
                )

                guild?.kick(userArg.id, reason)

                respond {
                    content = "Kicked ${userArg.username}"
                }

                actionLog.createEmbed {
                    color = DISCORD_BLACK
                    title = "Kicked User"
                    description = "Kicked ${userArg.mention} from the server\n${userArg.id} (${userArg.tag})"
                    field {
                        name = "Reason:"
                        value = reason
                        inline = false
                    }
                    field {
                        name = "User Notification:"
                        value =
                            if (dm != null) {
                                "User notified with a direct message"
                            } else {
                                "Failed to notify user with direct message"
                            }
                        inline = false
                    }
                    footer {
                        text = "Requested by ${user.asUser().tag}"
                        icon = user.asUser().avatar?.url
                    }
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

    inner class KickArgs : Arguments() {
        val user by user {
            name = "user"
            description = "User to kick"
        }
        val reason by defaultingString {
            name = "reason"
            description = "Reason for the kick"
            defaultValue = "No reason provided"
        }
    }
}