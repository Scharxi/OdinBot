package me.scharxidev.odin.database

import com.akuleshov7.ktoml.file.TomlFileReader
import com.zaxxer.hikari.HikariConfig
import kotlinx.serialization.Serializable

@Serializable
data class DatabaseUser(
    val username: String,
    val host: String = "localhost",
    val password: String = "",
    val databaseName: String,
    val port: Long = 3306,
) {
    companion object {
       fun fromTomlToConfig(editsBlock: HikariConfig.() -> Unit): HikariConfig {
           val res = TomlFileReader.partiallyDecodeFromFile(serializer(), "database.toml", "database")
           return HikariConfig().apply {
               username = res.username
               password = res.password
               jdbcUrl = "jdbc:mysql://${res.host}:${res.port}/${res.databaseName}"
               editsBlock()
           }
       }
    }
}
