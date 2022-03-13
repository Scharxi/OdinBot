package me.scharxidev.odin.extensions.moderation

import com.kotlindiscord.kord.extensions.DISCORD_GREEN
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalInt
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.supplier.EntitySupplyStrategy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import me.scharxidev.odin.database.DatabaseHelper
import me.scharxidev.odin.database.DatabaseManager
import me.scharxidev.odin.extensions.Moderation
import me.scharxidev.odin.util.ResponseHelper

class PurgeCommand : Moderation() {
    override suspend fun setup() {
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

}