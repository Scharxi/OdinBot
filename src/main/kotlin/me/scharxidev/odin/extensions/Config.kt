package me.scharxidev.odin.extensions

import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.channel
import com.kotlindiscord.kord.extensions.commands.converters.impl.role
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.GuildMessageChannelBehavior
import me.scharxidev.odin.database.DatabaseHelper
import me.scharxidev.odin.database.DatabaseManager
import me.scharxidev.odin.util.ResponseHelper
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class Config : Extension() {
    override val name: String = "config"

    override suspend fun setup() {
        ephemeralSlashCommand {
            name = "config"
            description = "Setup set up command"

            ephemeralSubCommand(::Config) {
                name = "set"
                description = "Set the config"

                check { hasPermission(Permission.Administrator) }

                action {
                    val actionLogId: String?

                    val isAlreadySet: Boolean = DatabaseHelper.selectFromConfig(guild!!.id,
                        DatabaseManager.Config.guildId).isDefined()

                    if (!isAlreadySet) {
                        newSuspendedTransaction {
                            DatabaseManager.Config.insertIgnore {
                                it[guildId] = guild!!.id.toString()
                                it[moderatorPing] = arguments.modRole.id.toString()
                                it[modActionLog] = arguments.modActionLog.id.toString()
                                it[messagesLog] = arguments.messageLog.id.toString()
                                it[joinChannel] = arguments.joinChannel.id.toString()
                            }
                        }

                        actionLogId =
                            DatabaseHelper.selectFromConfig(guild!!.id, DatabaseManager.Config.modActionLog).orNull()

                        respond { content = "Config set for guild id: ${guild!!.id}" }

                        val actionLogChannel =
                            guild!!.getChannel(Snowflake(actionLogId!!)) as GuildMessageChannelBehavior
                        ResponseHelper.responseEmbedInChannel(
                            actionLogChannel,
                            "Configuration set!",
                            "An administrator has set the config for this guild",
                            null,
                            user.asUser()
                        )
                    } else {
                        respond {
                            content = "Your configuration is already set. Please clear it first before updating!"
                        }
                    }
                }
            }
            ephemeralSubCommand {
                name = "clear"
                description = "Clears the config!"

                check { hasPermission(Permission.Administrator) }

                action {
                    var error = false

                    val guildConfig = DatabaseHelper.selectFromConfig(guild!!.id, DatabaseManager.Config.guildId)
                    val actionLog = DatabaseHelper.selectFromConfig(guild!!.id, DatabaseManager.Config.modActionLog)

                    val noConfigSetText = "**Error:** There is no configuration set for this guild!"

                    if (guildConfig.isEmpty() || actionLog.isEmpty()) {
                        respond {
                            content = noConfigSetText
                        }
                        return@action
                    }

                    newSuspendedTransaction {
                        try {
                            DatabaseManager.Config.deleteWhere {
                                DatabaseManager.Config.guildId eq guild!!.id.toString()
                            }
                        } catch (e: NoSuchElementException) {
                            respond {
                                content = noConfigSetText
                            }
                            error = true
                        }
                    }

                    if (error) return@action

                    respond { content = "Cleared config for guild id: ${guildConfig.orNull()!!}" }

                    val actionLogChannel = guild!!.getChannel(Snowflake(actionLog.orNull()!!)) as GuildMessageChannelBehavior
                    ResponseHelper.responseEmbedInChannel(
                        actionLogChannel,
                        "Configuration cleared",
                        "An administrator has cleared the configuration for this guild!",
                        null,
                        user.asUser()
                    )
                }
            }
        }
    }

    inner class Config : Arguments() {
        val modRole by role {
            name = "modRole"
            description = "Your moderator role"
        }
        val modActionLog by channel {
            name = "modActionLog"
            description = "Your mod action channel"
        }
        val messageLog by channel {
            name = "messageLogs"
            description = "Your messages log channel"
        }
        val joinChannel by channel {
            name = "joinChannel"
            description = "Your join log channel"
        }
    }
}