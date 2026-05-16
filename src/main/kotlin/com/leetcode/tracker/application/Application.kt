package com.leetcode.tracker.application

import com.leetcode.tracker.api.authRoutes
import com.leetcode.tracker.api.AuthenticatedUser
import com.leetcode.tracker.api.userRoutes
import com.leetcode.tracker.client.LeetCodeGraphqlClient
import com.leetcode.tracker.domain.ErrorResponse
import com.leetcode.tracker.domain.HealthResponse
import com.leetcode.tracker.data.DatabaseFactory
import com.leetcode.tracker.data.ProgressRepository
import com.leetcode.tracker.data.UserRepository
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.util.UUID

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val jdbcUrl = System.getenv("DATABASE_URL")
        ?: environment.config.propertyOrNull("database.jdbcUrl")?.getString()
        ?: "jdbc:postgresql://localhost:5432/leetcode_tracker"
    val dbUser = System.getenv("DATABASE_USER")
        ?: environment.config.propertyOrNull("database.user")?.getString()
        ?: "tracker"
    val dbPassword = System.getenv("DATABASE_PASSWORD")
        ?: environment.config.propertyOrNull("database.password")?.getString()
        ?: "tracker"

    val jwtSecret = System.getenv("JWT_SECRET")
        ?: environment.config.propertyOrNull("jwt.secret")?.getString()
        ?: "dev-only-secret-change-me-please-use-32chars-minimum!!"

    DatabaseFactory.init(jdbcUrl, dbUser, dbPassword)

    val users = UserRepository()
    val progress = ProgressRepository()
    val leetcodeClient = LeetCodeGraphqlClient()
    val sync = ProgressSyncService(leetcodeClient, progress)
    val jwtService = JwtService(jwtSecret)

    environment.monitor.subscribe(ApplicationStopped) {
        leetcodeClient.close()
    }

    install(CallLogging) {
        level = Level.INFO
    }

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                prettyPrint = false
            },
        )
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "LeetCode Progress Tracker"
            verifier(jwtService.verifier())
            validate { credential ->
                val sub = credential.payload.subject ?: return@validate null
                val userId = runCatching { UUID.fromString(sub) }.getOrNull() ?: return@validate null
                val email = credential.payload.getClaim("email").asString() ?: return@validate null
                AuthenticatedUser(userId, email)
            }
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(cause.message ?: "Internal server error"),
            )
        }
    }

    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }
        authRoutes(users, jwtService)
        userRoutes(users, progress, sync)
    }
}
