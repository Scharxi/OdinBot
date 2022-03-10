package me.scharxidev.odin.events

import com.kotlindiscord.kord.extensions.DISCORD_BLURPLE
import com.kotlindiscord.kord.extensions.checks.isNotBot
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import dev.kord.common.entity.MessageType
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.entity.Message
import dev.kord.core.event.message.MessageDeleteEvent

class GhostPingDetector : Extension() {
    override val name: String = "ghostpingdetector"

    override suspend fun setup() {
        event<MessageDeleteEvent> {
            check { isNotBot() }

            action {
                with(event) {
                    message?.let {
                        when {
                            it.isRef() -> {
                                message!!.referencedMessage!!.let { refMsg ->
                                    when {
                                        refMsg.containsGhostPing() -> sendGhostPingEmbed(channel, refMsg, true)
                                        refMsg.containsMentionedUser() -> sendGhostPingEmbed(channel, refMsg, false)
                                        else -> return@action
                                    }
                                }
                            }
                            it.containsGhostPing() -> sendGhostPingEmbed(channel, it, false)
                            it.containsMentionedUser() -> sendGhostPingEmbed(channel, it, false)
                        }
                    }
                }
            }
        }
    }

    private suspend fun sendGhostPingEmbed(
        channel: MessageChannelBehavior,
        message: Message,
        isMsgRef: Boolean = false,
    ) {
        if (isMsgRef) {
            channel.createEmbed {
                title = "Ghost Ping detected"
                description = "Found a ghost ping in following message:\n**${message.content}**"
                field {
                    name = "Mentioned User"
                    value = message.author?.tag ?: "None"
                }
                footer {
                    text = "Ghost Ping sent by ${message.author?.tag ?: "None"}"
                    icon = message.author?.avatar?.url
                }
                color = DISCORD_BLURPLE
            }
        } else {
            channel.createEmbed {
                title = "Ghost Ping detected"
                description = "Found a ghost ping in following message:\n**${message.content}**"
                footer {
                    text = "Ghost Ping sent by ${message.author?.tag ?: "None"}"
                    icon = message.author?.avatar?.url
                }
                color = DISCORD_BLURPLE
            }
        }
    }

}

private fun Message.containsGhostPing(): Boolean {
    return when {
        "@here" in this.content || "@everyone" in this.content -> true
        else -> false
    }
}

private fun Message.containsMentionedUser(): Boolean {
    return this.mentionedUserBehaviors.isNotEmpty()
}

private fun Message.isRef(): Boolean =
    this.type == MessageType.Reply

