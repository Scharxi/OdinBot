package me.scharxidev.odin.extensions.moderation

import com.kotlindiscord.kord.extensions.DISCORD_GREEN
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.getOrThrow
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.behavior.channel.edit
import dev.kord.core.entity.channel.TextChannel
import me.scharxidev.odin.database.DatabaseHelper
import me.scharxidev.odin.database.DatabaseManager
import me.scharxidev.odin.extensions.Moderation
import me.scharxidev.odin.util.ResponseHelper

class SlowModeCommand: Moderation() {
    override suspend fun setup() {
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
    }

    inner class SlowMode : Arguments() {
        val seconds by int {
            name = "seconds"
            description = "Number of seconds to limit the users rate limit"
        }
    }
}