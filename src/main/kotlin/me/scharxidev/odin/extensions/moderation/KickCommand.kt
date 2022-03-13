package me.scharxidev.odin.extensions.moderation

import com.kotlindiscord.kord.extensions.DISCORD_BLACK
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingString
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.behavior.channel.createEmbed
import kotlinx.coroutines.flow.toList
import me.scharxidev.odin.database.DatabaseHelper
import me.scharxidev.odin.database.DatabaseManager
import me.scharxidev.odin.util.ResponseHelper
import mu.KotlinLogging

class KickCommand : Extension() {

    override val name: String
        get() = "kickcommand"

    override suspend fun setup() {
        val logger = KotlinLogging.logger { }

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