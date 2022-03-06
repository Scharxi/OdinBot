package me.scharxidev.odin.database

import com.zaxxer.hikari.HikariDataSource
import mu.KLogger
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseManager {
    private val logger: KLogger = KotlinLogging.logger { }
    private var dataSource: HikariDataSource

    init {
        logger.info("Fetching data from database.toml")
        val config = DatabaseUser.fromTomlToConfig {
            driverClassName = "com.mysql.cj.jdbc.Driver"
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }
        dataSource = HikariDataSource(config)
        Database.connect(dataSource)
        logger.info("Successfully connected to database.")
    }

    object Warn : Table("warns") {
        val id = varchar("id", 128)
        val points = integer("points").nullable()

        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    fun startDatabase() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Warn)
        }
    }
}