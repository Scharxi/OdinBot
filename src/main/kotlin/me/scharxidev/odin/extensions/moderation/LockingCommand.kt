package me.scharxidev.odin.extensions.moderation

import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingBoolean
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingString
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalChannel
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.edit
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.rest.builder.channel.addRoleOverwrite

class LockingCommand : Extension() {
    override val name: String
        get() = "locking"

    override suspend fun setup() {
        ephemeralSlashCommand(::ChannelLockArgs) {
            name = "lock"
            description = "Locks a channel"

            check { hasPermission(Permission.ManageChannels) }

            action {
                val textChannel = (arguments.channel ?: channel.asChannel()).asChannelOf<TextChannel>()
                val reason = arguments.reason
                val notify = arguments.notify

                textChannel.edit {
                    name = textChannel.name + "-🔒"
                    addRoleOverwrite(guild!!.id) {
                        denied = Permissions(Permission.SendMessages, Permission.AddReactions)
                    }
                }

                if (notify) {
                    channel.createEmbed {
                        title = "Channel has been locked"
                        description = "This channel has been locked!\n**Reason:**\n${reason}"
                        footer {
                            text = "Requested by ${user.asUser().tag}"
                            icon = user.asUser().avatar?.url
                        }
                    }
                }

                respond {
                    content = "Locked ${textChannel.mention}"
                }

                // TODO: 13.03.2022 Send Action Log
            }
        }

        ephemeralSlashCommand(::ChannelUnLockArgs) {
            name = "unlock"
            description = "Unlocks a channel"

            check { hasPermission(Permission.ManageChannels) }

            action {
                val channel = (arguments.channel ?: channel.asChannel()).asChannelOf<TextChannel>()
                val notify = arguments.notify
                val isLocked: (TextChannel) -> Boolean = {
                    it.name.contains("-🔒") && it.getPermissionOverwritesForRole(guild!!.id)!!.denied in Permissions(
                        Permission.SendMessages,
                        Permission.AddReactions)
                }

                if (!isLocked(channel)) {
                    respond {
                        content = "This channel is not locked!"
                    }
                    return@action
                }

                channel.edit {
                    name = channel.name.removeSuffix("-🔒")
                    addRoleOverwrite(guild!!.id) {
                        allowed = Permissions(Permission.SendMessages, Permission.AddReactions)
                    }
                }

                if (notify) {
                    channel.createEmbed {
                        title = "Channel unlocked"
                        description = "This channel has been unlocked!\n"
                        footer {
                            text = "Requested by ${user.asUser().tag}"
                            icon = user.asUser().avatar?.url
                        }
                    }
                }

                respond {
                    content = "Successfully unlocked ${channel.mention}"
                }

                // TODO: 13.03.2022 Send Action Log
            }
        }
    }

    inner class ChannelLockArgs : Arguments() {
        val channel by optionalChannel {
            name = "channel"
            description = "Channel to lock"
        }
        val reason by defaultingString {
            name = "reason"
            description = "The reason for the channel lock"
            defaultValue = "No reason provided"
        }
        val notify by defaultingBoolean {
            name = "notify"
            description = "Whether a message should be sent in the channel, why the channel was locked"
            defaultValue = false
        }
    }

    inner class ChannelUnLockArgs : Arguments() {
        val channel by optionalChannel {
            name = "channel"
            description = "The channel you want to unlock"
        }
        val notify by defaultingBoolean {
            name = "notify"
            description = "Whether a message should be sent in the channel, why the channel was locked"
            defaultValue = false
        }

    }

}