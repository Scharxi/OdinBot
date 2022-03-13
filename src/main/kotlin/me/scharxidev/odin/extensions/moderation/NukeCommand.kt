package me.scharxidev.odin.extensions.moderation

import com.kotlindiscord.kord.extensions.DISCORD_GREEN
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import dev.kord.core.supplier.EntitySupplyStrategy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import me.scharxidev.odin.database.DatabaseHelper
import me.scharxidev.odin.database.DatabaseManager
import me.scharxidev.odin.extensions.Moderation
import me.scharxidev.odin.util.ResponseHelper

class NukeCommand : Moderation() {
    override suspend fun setup() {
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
    }
}