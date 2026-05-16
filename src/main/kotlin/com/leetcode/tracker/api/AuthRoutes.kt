package com.leetcode.tracker.api

import at.favre.lib.crypto.bcrypt.BCrypt
import com.leetcode.tracker.application.JwtService
import com.leetcode.tracker.data.UserRepository
import com.leetcode.tracker.domain.AuthResponse
import com.leetcode.tracker.domain.ErrorResponse
import com.leetcode.tracker.domain.LoginRequest
import com.leetcode.tracker.domain.RegisterRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.exceptions.ExposedSQLException

fun Route.authRoutes(
    users: UserRepository,
    jwt: JwtService,
) {
    route("/auth") {
        post("/register") {
            val body = call.receive<RegisterRequest>()
            if (body.email.isBlank() || body.password.length < 8) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid email or password (min 8 chars)"))
                return@post
            }
            val email = body.email.trim().lowercase()
            val hash = BCrypt.withDefaults().hashToString(12, body.password.toCharArray())
            val id = try {
                users.create(email, hash)
            } catch (e: ExposedSQLException) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("Email already registered"))
                return@post
            } catch (e: Exception) {
                if (e.message?.contains("unique", ignoreCase = true) == true) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse("Email already registered"))
                    return@post
                }
                throw e
            }
            val token = jwt.generate(id, email)
            call.respond(HttpStatusCode.Created, AuthResponse(token = token, userId = id.toString(), email = email))
        }

        post("/login") {
            val body = call.receive<LoginRequest>()
            val email = body.email.trim().lowercase()
            val user = users.findByEmail(email)
            if (user == null || !BCrypt.verifyer().verify(body.password.toCharArray(), user.passwordHash).verified) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid credentials"))
                return@post
            }
            val token = jwt.generate(user.id, user.email)
            call.respond(AuthResponse(token = token, userId = user.id.toString(), email = user.email))
        }
    }
}
