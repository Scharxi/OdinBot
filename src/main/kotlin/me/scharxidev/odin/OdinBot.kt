package me.scharxidev.odin

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.utils.env
import dev.kord.common.entity.PresenceStatus
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import me.scharxidev.odin.database.DatabaseManager
import me.scharxidev.odin.events.GhostPingDetector
import me.scharxidev.odin.extensions.Config
import me.scharxidev.odin.extensions.Moderation
import me.scharxidev.odin.extensions.moderation.ClearCommand

private val BOT_TOKEN = env("TOKEN")
private val TEST_GUILD = env("TEST_GUILD")

@PrivilegedIntent
suspend fun main() {
    ExtensibleBot(BOT_TOKEN) {
        applicationCommands {
            defaultGuild(TEST_GUILD)
            enabled = true
        }
        extensions {
            add(::Config)
            add(::Moderation)
            add(::ClearCommand)
            add(::GhostPingDetector)
        }
        presence {
            status = PresenceStatus.Online
            playing("Odin is with you!")
        }
        intents {
            +Intent.GuildMembers
            +Intent.GuildMessages
            +Intent.Guilds
        }
        hooks {
            afterKoinSetup {
                DatabaseManager.startDatabase()
            }
        }
    }.start()
}