package me.scharxidev.odin.extensions.moderation

import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingInt
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingString
import com.kotlindiscord.kord.extensions.commands.converters.impl.user
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.ban
import kotlin.time.ExperimentalTime

class TempBanCommand : Extension() {
    override val name: String
        get() = "tempbancommand"

    @OptIn(ExperimentalTime::class)
    override suspend fun setup() {
        ephemeralSlashCommand(::TempBanCommandArgs) {
            name = "temp-ban"
            description = "Temporarily bans an user"

            check { hasPermission(Permission.ModerateMembers) }

            action {
                val userToBan = arguments.user
                val reason = arguments.reason
                //val duration = Clock.System.now().plus(arguments.time, TimeZone.currentSystemDefault())

                guild!!.ban(userToBan.id) {
                    this.reason = reason
                }

                respond {
                    content = "Banned ${userToBan.mention} for ${arguments.time}"
                }
            }
        }
    }

    inner class TempBanCommandArgs : Arguments() {
        val user by user {
            name = "user"
            description = "The user you want to ban temporarily"
        }
        val time by defaultingInt {
            name = "days"
            description = "How long you want the user to be muted"
            defaultValue = 1
        }
        val reason by defaultingString {
            name = "reason"
            description = "The reason for the temporary ban"
            defaultValue = "No reason provided"
        }
    }
}