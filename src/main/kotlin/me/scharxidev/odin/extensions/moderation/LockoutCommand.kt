package me.scharxidev.odin.extensions.moderation

import com.kotlindiscord.kord.extensions.DISCORD_BLACK
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingString
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalChannel
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.editMemberPermission
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.TextChannel
import me.scharxidev.odin.database.DatabaseHelper
import me.scharxidev.odin.database.DatabaseManager
import me.scharxidev.odin.util.ResponseHelper

class LockoutCommand : Extension() {
    override val name: String
        get() = "lockoutcommand"

    override suspend fun setup() {
        ephemeralSlashCommand(::LockoutCommandArgs) {
            name = "lockout"
            description = "Locks a user out of a channel"

            check { hasPermission(Permission.ManageChannels) }
            check { hasPermission(Permission.ModerateMembers) }

            action {
                val userArg: User = arguments.user
                val channel = (arguments.channel ?: channel.asChannel()).asChannelOf<TextChannel>()
                val reason = arguments.reason

                // TODO: 14.03.2022 Moderator checks

                channel.editMemberPermission(userArg.id) {
                    denied = Permissions(Permission.SendMessages, Permission.AddReactions)
                }

                respond {
                    content = "Successfully locked out ${userArg.mention} from ${channel.mention}"
                }

                val dm = ResponseHelper.userDmEmbed(
                    userArg,
                    "You have been locked out from **${channel.name}** in **${guild?.fetchGuild()?.name} Guild**",
                    "**Reason:**\n${reason}",
                    null
                )

                val actionLogId = DatabaseHelper.selectFromConfig(guild!!.id, DatabaseManager.Config.modActionLog)
                if (actionLogId.isEmpty()) {
                    respond {
                        content =
                            "**Error:** Unable to access config for this guild! Is your configuration set?"
                    }
                    return@action
                }

                val actionLog = guild?.getChannel(Snowflake(actionLogId.orNull()!!)) as GuildMessageChannelBehavior

                actionLog.createEmbed {
                    color = DISCORD_BLACK
                    title = "Locked out user"
                    description = "Locked out ${userArg.mention} from ${channel.mention}"
                    field {
                        name = "Reason"
                        value = reason
                    }
                    field {
                        name = "User Notification"
                        value =
                            if (dm != null) {
                                "User notified with a direct message"
                            } else {
                                "Failed to notify user with direct message"
                            }
                    }
                    footer {
                        text = "Requested by ${user.asUser().tag}"
                        icon = user.asUser().avatar?.url
                    }
                }
            }
        }

        ephemeralSlashCommand(::LockoutRemoveCommandArgs) {
            name = "lockout-rmv"
            description = "Removes a lockout from an user"

            check { hasPermission(Permission.ModerateMembers) }
            check { hasPermission(Permission.ManageChannels) }

            action {
                val userArg = arguments.user
                val channel = (arguments.channel ?: channel.asChannel()).asChannelOf<TextChannel>()

                // TODO: 14.03.2022 Make moderator checks

                channel.editMemberPermission(userArg.id) {
                    allowed = Permissions(Permission.SendMessages, Permission.AddReactions)
                }

                respond {
                    content = "Successfully removed lockout of ${userArg.mention} from ${channel.mention}"
                }

                val actionLogId = DatabaseHelper.selectFromConfig(guild!!.id, DatabaseManager.Config.modActionLog)
                if (actionLogId.isEmpty()) {
                    respond {
                        content =
                            "**Error:** Unable to access config for this guild! Is your configuration set?"
                    }
                    return@action
                }

                val actionLog = guild?.getChannel(Snowflake(actionLogId.orNull()!!)) as GuildMessageChannelBehavior

                actionLog.createEmbed {
                    color = DISCORD_BLACK
                    title = "Removed lockout"
                    description = "Removed lockout of ${userArg.mention} from ${channel.mention}"
                    footer {
                        text = "Requested by ${user.asUser().tag}"
                        icon = user.asUser().avatar?.url
                    }
                }

            }
        }
    }

    inner class LockoutCommandArgs : Arguments() {
        val user by user {
            name = "user"
            description = "The user to lockout"
        }
        val channel by optionalChannel {
            name = "channel"
            description = "The channel from which the user should be locked out"
        }
        val reason by defaultingString {
            name = "reason"
            description = "The reason for the lockout"
            defaultValue = "No reason provided"
        }
    }

    inner class LockoutRemoveCommandArgs : Arguments() {
        val user by user {
            name = "user"
            description = "The user whose lockout you want to remove"
        }
        val channel by optionalChannel {
            name = "channel"
            description = "The channel from which the user should be locked in"
        }
    }

}