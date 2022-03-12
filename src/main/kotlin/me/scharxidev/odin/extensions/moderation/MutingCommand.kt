package me.scharxidev.odin.extensions.moderation

import com.kotlindiscord.kord.extensions.DISCORD_BLACK
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.coalescingDefaultingDuration
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingString
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.time.TimestampType
import com.kotlindiscord.kord.extensions.time.toDiscord
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.removeTimeout
import com.kotlindiscord.kord.extensions.utils.timeoutUntil
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.edit
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import me.scharxidev.odin.database.DatabaseHelper
import me.scharxidev.odin.database.DatabaseManager
import me.scharxidev.odin.extensions.Moderation
import me.scharxidev.odin.util.ResponseHelper
import mu.KotlinLogging

class MutingCommand : Moderation() {
    override suspend fun setup() {
        val logger = KotlinLogging.logger { }
        ephemeralSlashCommand(::TempMuteArgs) {
            name = "temp-mute"
            description = "Mutes a user temporarily"

            check { hasPermission(Permission.ModerateMembers) }

            action {
                val userArg = arguments.user
                val reason = arguments.reason

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

                val duration = Clock.System.now().plus(arguments.time, TimeZone.currentSystemDefault())

                try {
                    val roles = userArg.asMember(guild!!.id).roles.toList().map { it.id }

                    if (guild?.getMember(userArg.id)?.isBot == true) {
                        respond {
                            content = "You cannot timeout a bot!"
                        }
                        return@action
                    } else if (Snowflake(moderators.orNull()!!) in roles) {
                        respond {
                            content = "You cannot timeout a moderator!"
                        }
                        return@action
                    }
                } catch (e: Exception) {
                    logger.warn("IsBot and Moderator checks failed on `Timeout` due to error")
                }

                try {
                    guild?.getMember(userArg.id)?.edit {
                        timeoutUntil = duration
                    }
                } catch (e: Exception) {
                    respond {
                        content = "Sorry, I can't timeout this person! Try doing timeout manually instead!"
                    }
                }

                val dm = ResponseHelper.userDmEmbed(
                    userArg,
                    "You have been timed out in ${guild?.fetchGuild()?.name}",
                    "**Duration:**\n${
                        duration.toDiscord(TimestampType.Default) + "(" + arguments.time.toString()
                            .replace("PT", "") + ")"
                    }\n**Reason:**\n${reason}",
                    null
                )

                respond {
                    content = "Timed out ${userArg.username}"
                }

                actionLog.createEmbed {
                    title = "Timeout"
                    color = DISCORD_BLACK
                    timestamp = Clock.System.now()

                    field {
                        name = "User:"
                        value = "${userArg.tag} \n${userArg.id}"
                        inline = false
                    }
                    field {
                        name = "Duration:"
                        value = duration.toDiscord(TimestampType.Default) + " (" + arguments.time.toString()
                            .replace("PT", "") + ")"
                        inline = false
                    }
                    field {
                        name = "Reason:"
                        value = arguments.reason
                        inline = false
                    }
                    field {
                        name = "User notification"
                        value =
                            if (dm != null) {
                                "User notified with a direct message"
                            } else {
                                "Failed to notify user with a direct message "
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

        ephemeralSlashCommand(::UnmuteArgs) {
            name = "unmute"
            description = "Unmutes an user"

            check { hasPermission(Permission.ModerateMembers) }

            action {
                val userArg = arguments.user

                if (userArg.asMember(guild!!.id).timeoutUntil == null) {
                    respond {
                        content = "Could not unmute this user, because he is not muted"
                    }
                    return@action
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

                guild?.getMember(userArg.id)?.removeTimeout()

                respond {
                    content = "Unmuted ${userArg.tag}"
                }

                actionLog.createEmbed {
                    title = "Remove Timeout"
                    color = DISCORD_BLACK
                    timestamp = Clock.System.now()

                    field {
                        name = "User:"
                        value = "${userArg.tag} \n${userArg.id}"
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

    inner class TempMuteArgs : Arguments() {
        val user by user {
            name = "user"
            description = "The user you want to temp mute"
        }
        val time by coalescingDefaultingDuration {
            name = "time"
            description = "How long you want the user to be muted"
            defaultValue = DateTimePeriod(hours = 1)
        }
        val reason by defaultingString {
            name = "reason"
            description = "The reason for the temp mute"
            defaultValue = "No reason provided"
        }
    }

    inner class UnmuteArgs : Arguments() {
        val user by user {
            name = "user"
            description = "The user to unmute"
        }
    }
}