package me.scharxidev.odin.extensions.report

import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalChannel
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.entity.channel.TextChannel
import me.scharxidev.odin.database.DatabaseHelper
import me.scharxidev.odin.database.DatabaseManager
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ReportPreferencesCommand : Extension() {
    override val name: String
        get() = "reportpreferences"

    override suspend fun setup() {
        ephemeralSlashCommand {
            name = "report-pref"
            description = "Setup the report system"

            ephemeralSubCommand(::ReportPreferencesCommandArgs) {
                name = "set"
                description = "Set the preferences"

                check { hasPermission(Permission.Administrator) }

                action {
                    val channel = (arguments.reportLog ?: channel.asChannel()).asChannelOf<TextChannel>()
                    val isAlreadySet: Boolean = DatabaseHelper.selectFromReportPreferences(guild!!.id,
                        DatabaseManager.ReportPreference.reportLogChannel).isDefined()

                    if (!isAlreadySet) {
                        newSuspendedTransaction {
                            DatabaseManager.ReportPreference.insertIgnore {
                                it[guildId] = guild!!.id.toString()
                                it[reportLogChannel] = channel.id.toString()
                            }
                        }

                        respond { content = "Preferences set for guild id: ${guild!!.id}" }

                        // TODO: 15.03.2022 Send action log

                    } else {
                        respond {
                            content = "Your already set your preferences. Please clear it first before updating!"
                        }
                    }
                }
            }

            ephemeralSubCommand {
                name = "clear"
                description = "Resets the preferences"

                check { hasPermission(Permission.Administrator) }

                action {
                    var error = false

                    val preferences =
                        DatabaseHelper.selectFromReportPreferences(guild!!.id, DatabaseManager.ReportPreference.guildId)
                    val reportLog = DatabaseHelper.selectFromReportPreferences(guild!!.id,
                        DatabaseManager.ReportPreference.reportLogChannel)

                    val noPreferencesText = "**Error:** No preferences set for this guild!"

                    if (preferences.isEmpty() || reportLog.isEmpty()) {
                        respond {
                            content = noPreferencesText
                        }
                        return@action
                    }

                    newSuspendedTransaction {
                        try {
                            DatabaseManager.ReportPreference.deleteWhere {
                                DatabaseManager.ReportPreference.guildId eq guild!!.id.toString()
                            }
                        } catch (e: NoSuchElementException) {
                            respond {
                                content = noPreferencesText
                            }
                            error = true
                        }
                    }

                    if (error) return@action

                    respond { content = "Reset the preferences for guild id: ${guild!!.id}" }

                    // TODO: 15.03.2022 Send action log
                }
            }
        }
    }

    inner class ReportPreferencesCommandArgs : Arguments() {
        val reportLog by optionalChannel {
            name = "reportLog"
            description = "The channel in which the reports are to be stored"
        }
    }
}