package me.scharxidev.odin.database

import arrow.core.*
import dev.kord.common.entity.Snowflake
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Duration

object DatabaseHelper {
    suspend fun selectFromConfig(guildId: Snowflake, column: Column<String>): Option<String> {
        return newSuspendedTransaction {
            try {
                 Some(DatabaseManager.Config.select {
                    DatabaseManager.Config.guildId eq guildId.toString()
                }.single()[column])
            } catch (e: NoSuchElementException) {
                None
            }
        }
    }

    suspend fun selectFromReportPreferences(guildId: Snowflake, column: Column<String>): Option<String> {
        return newSuspendedTransaction {
            try {
                Some(DatabaseManager.ReportPreference.select {
                    DatabaseManager.ReportPreference.guildId eq guildId.toString()
                }.single()[column])
            }catch (e: NoSuchElementException) {
                None
            }
        }
    }

    suspend fun tempBan(guildId: Snowflake, userId: Snowflake, duration: Duration) {
        newSuspendedTransaction {
            DatabaseManager.TempBan.insertIgnore {
                it[this.guildId] = guildId.toString()
                it[this.userId] = userId.toString()
                it[expiration] = duration
                it[active] = false
            }
        }
    }

    suspend fun getAllTempBans(guildId: Snowflake): Flow<Snowflake> {
        return newSuspendedTransaction {
            flowOf(DatabaseManager.TempBan.select {
                DatabaseManager.TempBan.guildId eq guildId.toString()
            }.toList().map { it[DatabaseManager.TempBan.userId] }).map { Snowflake(it.toString()) }
        }
    }
}