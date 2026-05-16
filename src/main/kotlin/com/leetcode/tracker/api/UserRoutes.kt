package com.leetcode.tracker.api

import com.leetcode.tracker.application.GoalProgressCalculator
import com.leetcode.tracker.application.ProgressSyncService
import com.leetcode.tracker.client.LeetCodeClientException
import com.leetcode.tracker.data.ProgressRepository
import com.leetcode.tracker.data.UserRepository
import com.leetcode.tracker.domain.BindLeetCodeRequest
import com.leetcode.tracker.domain.ErrorResponse
import com.leetcode.tracker.domain.LeetCodeBindResponse
import com.leetcode.tracker.domain.CreateGoalRequest
import com.leetcode.tracker.domain.GoalEntity
import com.leetcode.tracker.domain.PatternCatalog
import com.leetcode.tracker.domain.PatternProgressResponse
import com.leetcode.tracker.domain.StreakResponse
import com.leetcode.tracker.domain.UserStatsResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

fun Route.userRoutes(
    users: UserRepository,
    progress: ProgressRepository,
    sync: ProgressSyncService,
) {
    authenticate("auth-jwt") {
        route("/users/{id}") {
            get("/stats") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse( "Invalid user id"))
                val auth = call.principal<AuthenticatedUser>() ?: return@get call.respond(HttpStatusCode.Unauthorized, Unit)
                if (auth.userId != id) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse( "Forbidden"))

                val refresh = call.request.queryParameters["refresh"]?.equals("true", ignoreCase = true) == true
                val user = users.findById(id) ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse( "User not found"))
                if (refresh) {
                    val lc = user.leetcodeUsername
                    if (lc != null) {
                        try {
                            sync.sync(id, lc)
                        } catch (e: LeetCodeClientException) {
                            return@get call.respond(HttpStatusCode.BadGateway, ErrorResponse( (e.message ?: "LeetCode error")))
                        }
                    }
                }

                val stats = progress.getStats(id)
                call.respond(
                    UserStatsResponse(
                        leetcodeUsername = user.leetcodeUsername,
                        totalSolved = stats?.total ?: 0,
                        easySolved = stats?.easy ?: 0,
                        mediumSolved = stats?.medium ?: 0,
                        hardSolved = stats?.hard ?: 0,
                        ranking = stats?.ranking,
                        reputation = stats?.reputation,
                        lastSyncedAt = stats?.lastSyncedAt?.toString(),
                    ),
                )
            }

            get("/patterns") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse( "Invalid user id"))
                val auth = call.principal<AuthenticatedUser>() ?: return@get call.respond(HttpStatusCode.Unauthorized, Unit)
                if (auth.userId != id) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse( "Forbidden"))

                val rows = progress.listPatterns(id).associateBy { it.patternKey }
                val payload = PatternCatalog.canonicalKeys().sorted().map { key ->
                    val row = rows[key]
                    PatternProgressResponse(
                        pattern = key,
                        displayName = PatternCatalog.displayName(key),
                        solvedCount = row?.solvedCount ?: 0,
                        lastSyncedAt = row?.lastSyncedAt?.toString(),
                    )
                }
                call.respond(payload)
            }

            get("/streak") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse( "Invalid user id"))
                val auth = call.principal<AuthenticatedUser>() ?: return@get call.respond(HttpStatusCode.Unauthorized, Unit)
                if (auth.userId != id) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse( "Forbidden"))

                val s = progress.getStreak(id)
                call.respond(
                    StreakResponse(
                        currentStreakDays = s?.current ?: 0,
                        longestStreakDays = s?.longest ?: 0,
                        lastActiveDate = s?.lastActive?.toString(),
                        leetcodeCalendarStreak = s?.lcStreak,
                    ),
                )
            }

            post("/leetcode") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse( "Invalid user id"))
                val auth = call.principal<AuthenticatedUser>() ?: return@post call.respond(HttpStatusCode.Unauthorized, Unit)
                if (auth.userId != id) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse( "Forbidden"))

                val body = call.receive<BindLeetCodeRequest>()
                val username = body.leetcodeUsername.trim()
                if (username.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse( "leetcodeUsername is required"))
                }
                try {
                    sync.sync(id, username)
                } catch (e: LeetCodeClientException) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse( (e.message ?: "LeetCode error")))
                }
                users.updateLeetcodeUsername(id, username)
                call.respond(LeetCodeBindResponse(leetcodeUsername = username, synced = true))
            }

            post("/goals") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse( "Invalid user id"))
                val auth = call.principal<AuthenticatedUser>() ?: return@post call.respond(HttpStatusCode.Unauthorized, Unit)
                if (auth.userId != id) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse( "Forbidden"))

                val body = call.receive<CreateGoalRequest>()
                val patternKey = PatternCatalog.normalizeInput(body.pattern)
                if (!PatternCatalog.canonicalKeys().contains(patternKey)) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse( "Unknown pattern"))
                }
                val deadline = runCatching { LocalDate.parse(body.deadline) }.getOrElse {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse( "Invalid deadline (use ISO-8601 date yyyy-MM-dd)"))
                }
                if (body.targetTotalSolved <= 0) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse( "targetTotalSolved must be positive"))
                }

                val starting = progress.getPatternCount(id, patternKey)
                val goal = GoalEntity(
                    id = UUID.randomUUID(),
                    userId = id,
                    patternKey = patternKey,
                    targetTotalSolved = body.targetTotalSolved,
                    startingSolved = starting,
                    deadline = deadline,
                    createdAt = OffsetDateTime.now(),
                )
                progress.insertGoal(goal)
                val current = progress.getPatternCount(id, patternKey)
                call.respond(GoalProgressCalculator.build(goal, current))
            }

            get("/goals") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse( "Invalid user id"))
                val auth = call.principal<AuthenticatedUser>() ?: return@get call.respond(HttpStatusCode.Unauthorized, Unit)
                if (auth.userId != id) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse( "Forbidden"))

                val goals = progress.listGoals(id)
                val out = goals.map { g ->
                    val current = progress.getPatternCount(id, g.patternKey)
                    GoalProgressCalculator.build(g, current)
                }
                call.respond(out)
            }
        }
    }
}
