package com.leetcode.tracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init(jdbcUrl: String, user: String, password: String) {
        val ds = com.zaxxer.hikari.HikariDataSource(
            com.zaxxer.hikari.HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                username = user
                this.password = password
                maximumPoolSize = 10
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                validate()
            },
        )
        org.jetbrains.exposed.sql.Database.connect(ds)
        transaction {
            org.jetbrains.exposed.sql.SchemaUtils.createMissingTablesAndColumns(Users, Stats, Patterns, Goals, Streaks)
        }
    }
}

suspend fun <T> dbQuery(block: Transaction.() -> T): T =
    withContext(Dispatchers.IO) {
        transaction(statement = block)
    }
