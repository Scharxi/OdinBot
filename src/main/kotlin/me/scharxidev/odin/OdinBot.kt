package me.scharxidev.odin

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.utils.env
import dev.kord.common.entity.PresenceStatus

private val BOT_TOKEN = env("TOKEN")

suspend fun main() {
    ExtensibleBot(BOT_TOKEN) {
        presence {
            status = PresenceStatus.Online
            playing("Odin is with you!")
        }
    }.start()
}