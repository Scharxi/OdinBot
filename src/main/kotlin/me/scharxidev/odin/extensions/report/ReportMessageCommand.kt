package me.scharxidev.odin.extensions.report

import com.kotlindiscord.kord.extensions.DISCORD_GREEN
import com.kotlindiscord.kord.extensions.DISCORD_YELLOW
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.ephemeralButton
import com.kotlindiscord.kord.extensions.components.types.emoji
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralMessageCommand
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.time.TimestampType
import com.kotlindiscord.kord.extensions.time.toDiscord
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.getJumpUrl
import com.kotlindiscord.kord.extensions.utils.timeoutUntil
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Option
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.Optional
import dev.kord.common.entity.optional.optional
import dev.kord.core.behavior.ban
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.component.Component
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import kotlinx.datetime.Clock
import me.scharxidev.odin.database.DatabaseHelper
import me.scharxidev.odin.database.DatabaseManager
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

// TODO: 17.03.2022 Refactor this code
// TODO: 17.03.2022 Implement own event system
// TODO: 17.03.2022 Better Embeds

class ReportMessageCommand : Extension() {
    override val name: String
        get() = "reportmessage"

    @OptIn(ExperimentalTime::class)
    override suspend fun setup() {

        event<ButtonInteractionCreateEvent> {
            action {
                with(event) {
                    when (interaction.componentId) {
                        "kickButton", "banButton", "muteButton" -> {
                            val oldEmbed = interaction.message?.embeds!!.first().copy()
                            interaction.message?.edit {
                                embed {
                                    oldEmbed.apply(this)
                                    field {
                                        name = "Performed Action"
                                        value = "Kick"
                                    }
                                    field {
                                        inline = true
                                        name = "Performed on"
                                        value = Clock.System.now().toDiscord(TimestampType.ShortDateTime)
                                    }
                                    color = DISCORD_GREEN
                                }
                                components {
                                    removeAll()
                                }
                            }
                        }
                    }
                }
            }
        }

        ephemeralMessageCommand {
            name = "Report"

            action {
                val reportedMessage = targetMessages.first()
                val reportChannelId = DatabaseHelper.selectFromReportPreferences(guild!!.id,
                    DatabaseManager.ReportPreference.reportLogChannel)

                if (reportChannelId.isEmpty()) {
                    respond {
                        content = "**Error:** Unable to find the report preferences for this guild. Are they set?"
                    }
                    return@action
                }

                val reportChannel = guild!!.getChannel(Snowflake(reportChannelId.orNull()!!)).asChannelOf<TextChannel>()

                reportChannel.createMessage {
                    embed {
                        color = DISCORD_YELLOW
                        title = "Report"
                        description = "${member?.asUser()!!.mention} reported ${reportedMessage.author?.mention}"
                        field {
                            name = "Message"
                            value = reportedMessage.content
                        }
                        field {
                            name = "Channel"
                            value = reportedMessage.channel.mention
                        }
                        field {
                            name = "Link"
                            value = reportedMessage.getJumpUrl()
                        }
                        footer {
                            text = "Requested by ${member?.asUser()!!.tag}"
                            icon = member?.asUser()!!.avatar?.url
                        }
                    }
                    components(Duration.Companion.minutes(10)) {
                        ephemeralButton {
                            id = "kickButton"
                            label = "Kick"
                            emoji("👟")
                            style = ButtonStyle.Danger
                            action {
                                try {
                                    guild?.kick(reportedMessage.author!!.id, "Report Action")
                                } catch (e: NullPointerException) {
                                    respond {
                                        content = "Could not kick this user because he wasn't found"
                                    }
                                }

                                respond {
                                    content = "Kicked ${reportedMessage.author!!.username}"
                                }
                            }
                        }
                        ephemeralButton {
                            label = "Ban"
                            id = "banButton"
                            style = ButtonStyle.Danger
                            emoji("🔨")
                            action {
                                try {
                                    guild?.ban(reportedMessage.author!!.id) {
                                        reason = "Report Action"
                                    }
                                } catch (e: NullPointerException) {
                                    respond {
                                        content = "Could not ban this user because he wasn't found"
                                    }
                                }
                                respond {
                                    content = "Banned ${reportedMessage.author!!.username}"
                                }
                            }
                        }
                        ephemeralButton {
                            label = "Mute"
                            id = "muteButton"
                            style = ButtonStyle.Danger
                            emoji("🔈")
                            action {
                                try {
                                    println("Report Message Author: ${reportedMessage.author?.tag}")
                                    reportedMessage.author?.let {
                                        guild!!.getMember(it.id).edit {
                                            timeoutUntil = Clock.System.now().plus(Duration.Companion.hours(2))
                                        }
                                    }
                                } catch (e: NullPointerException) {
                                    respond {
                                        content = "Could not mute this user because he wasn't found"
                                    }
                                }
                                respond {
                                    content = "Muted ${reportedMessage.author!!.username} for 2 hours"
                                }
                            }
                            remove(this)
                        }
                        ephemeralButton {
                            label = "Delete"
                            id = "deleteButton"
                            style = ButtonStyle.Danger
                            emoji("🗑")
                            action {
                                try {
                                    reportedMessage.delete("Report Action")
                                } catch (e: NullPointerException) {
                                    respond {
                                        content = "Could not mute this user because he wasn't found"
                                    }
                                }
                                respond {
                                    content = "Deleted message of **${reportedMessage.author?.tag}**"
                                }
                            }

                        }
                        // TODO: 17.03.2022 Add Warn as button
                    }
                }
                respond {
                    content = targetMessages.first().content
                }
            }
        }
    }
}