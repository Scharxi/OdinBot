package me.scharxidev.odin.database

import arrow.core.*
import dev.kord.common.entity.Snowflake
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

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
}