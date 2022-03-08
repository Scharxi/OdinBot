package me.scharxidev.odin.util

import com.kotlindiscord.kord.extensions.DISCORD_BLACK
import dev.kord.common.Color
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.entity.Message
import dev.kord.core.entity.User
import kotlinx.datetime.Clock

object ResponseHelper {
    suspend fun responseEmbedInChannel(
        channel: MessageChannelBehavior,
        embedTitle: String?,
        embedDesc: String?,
        embedColor: Color?,
        requestedBy: User?,
    ): Message {
        return channel.createEmbed {
            embedTitle?.let { title = it }
            embedDesc?.let { description = it }
            color = embedColor ?: DISCORD_BLACK
            timestamp = Clock.System.now()
            requestedBy?.let {
                footer {
                    text = it.tag
                    icon = it.avatar?.url
                }
            }
        }
    }
}