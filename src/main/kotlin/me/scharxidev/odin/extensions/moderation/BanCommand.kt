package me.scharxidev.odin.extensions.moderation

import com.kotlindiscord.kord.extensions.DISCORD_BLACK
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingInt
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingString
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
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
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import me.scharxidev.odin.database.DatabaseHelper
import me.scharxidev.odin.database.DatabaseManager
import me.scharxidev.odin.util.ResponseHelper
import mu.KotlinLogging

class BanCommand : Extension() {
    override val name: String
        get() = "banning"

    override suspend fun setup() {
        val logger = KotlinLogging.logger { }

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