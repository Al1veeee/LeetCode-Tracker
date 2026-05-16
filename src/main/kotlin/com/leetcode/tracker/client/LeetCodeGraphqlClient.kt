package com.leetcode.tracker.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class LeetCodeGraphqlClient(
    private val endpoint: String = "https://leetcode.com/graphql",
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                },
            )
        }
    },
) {
    suspend fun fetchUserSnapshot(username: String): LeetCodeUserSnapshot {
        val full = runCatching { executeUserStats(username, withTags = true) }.getOrElse {
            executeUserStats(username, withTags = false)
        }

        val matched = full["matchedUser"] as? JsonObject
            ?: throw LeetCodeClientException("LeetCode user not found: $username")

        val profile = matched["profile"] as? JsonObject
        val ranking = (profile?.get("ranking") as? JsonPrimitive)?.intOrNull
        val reputation = (profile?.get("reputation") as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

        val submit = matched["submitStatsGlobal"] as? JsonObject
        val acList = submit?.get("acSubmissionNum") as? JsonArray
        var total = 0
        var easy = 0
        var medium = 0
        var hard = 0
        if (acList != null) {
            for (el in acList) {
                val o = el as? JsonObject ?: continue
                val diff = (o["difficulty"] as? JsonPrimitive)?.contentOrNull?.lowercase() ?: continue
                val count = (o["count"] as? JsonPrimitive)?.intOrNull ?: 0
                when (diff) {
                    "all" -> total = count
                    "easy" -> easy = count
                    "medium" -> medium = count
                    "hard" -> hard = count
                }
            }
        }

        val tagCounts = parseTagProblemCounts(matched["tagProblemCounts"])

        val year = LocalDate.now(ZoneOffset.UTC).year
        val calResponse = runCatching {
            execute(
                GraphqlHttpBody(
                    query = USER_CALENDAR_QUERY,
                    variables = buildJsonObject {
                        put("username", username)
                        put("year", year)
                    },
                ),
            )
        }.getOrNull()

        val calMatched = calResponse?.data?.get("matchedUser") as? JsonObject
        val userCalendar = calMatched?.get("userCalendar") as? JsonObject
        val submissionCalendar = (userCalendar?.get("submissionCalendar") as? JsonPrimitive)?.contentOrNull
        val dccStreak = (userCalendar?.get("dccStreak") as? JsonPrimitive)?.intOrNull
            ?: (userCalendar?.get("streak") as? JsonPrimitive)?.intOrNull

        val activityByDay = parseSubmissionCalendar(submissionCalendar)
        val computedDailyStreak = computeCurrentStreak(activityByDay, ZoneOffset.UTC)
        val longest = longestStreakDays(activityByDay)

        return LeetCodeUserSnapshot(
            username = username,
            totalSolved = total,
            easySolved = easy,
            mediumSolved = medium,
            hardSolved = hard,
            ranking = ranking,
            reputation = reputation,
            tagSolvedBySlug = tagCounts,
            leetcodeCalendarStreak = dccStreak,
            activityByUtcDate = activityByDay,
            computedDailyStreak = computedDailyStreak,
            longestStreakDays = longest,
        )
    }

    private suspend fun executeUserStats(username: String, withTags: Boolean): JsonObject {
        val query = if (withTags) USER_STATS_WITH_TAGS_QUERY else USER_STATS_MIN_QUERY
        val res = execute(
            GraphqlHttpBody(
                query = query,
                variables = buildJsonObject { put("username", username) },
            ),
        )
        return res.data ?: throw LeetCodeClientException(
            "LeetCode returned no data: ${res.errors?.joinToString { it.message }}",
        )
    }

    private suspend fun execute(body: GraphqlHttpBody): GraphqlResponse {
        val response: GraphqlResponse = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            headers {
                append("Referer", "https://leetcode.com/")
                append("User-Agent", "LeetCodeProgressTracker/1.0")
            }
            setBody(body)
        }.body()
        if (response.errors != null && response.errors.isNotEmpty() && response.data == null) {
            throw LeetCodeClientException(response.errors.joinToString { it.message })
        }
        return response
    }

    private fun parseTagProblemCounts(node: JsonElement?): Map<String, Int> {
        if (node !is JsonObject) return emptyMap()
        val out = mutableMapOf<String, Int>()
        for ((_, bucketValue) in node) {
            if (bucketValue !is JsonArray) continue
            for (el in bucketValue) {
                val o = el as? JsonObject ?: continue
                val slug = (o["tagSlug"] as? JsonPrimitive)?.contentOrNull
                    ?: (o["tagName"] as? JsonPrimitive)?.contentOrNull?.lowercase()?.replace(' ', '-')
                val solved = (o["problemsSolved"] as? JsonPrimitive)?.intOrNull ?: continue
                if (slug != null) {
                    out[slug.lowercase()] = maxOf(out[slug.lowercase()] ?: 0, solved)
                }
            }
        }
        return out
    }

    private fun parseSubmissionCalendar(raw: String?): Map<LocalDate, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        val obj = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return emptyMap()
        val map = mutableMapOf<LocalDate, Int>()
        for ((k, v) in obj) {
            val ts = k.toLongOrNull() ?: continue
            val count = (v as? JsonPrimitive)?.intOrNull ?: continue
            val date = Instant.ofEpochSecond(ts).atZone(ZoneOffset.UTC).toLocalDate()
            map[date] = count
        }
        return map
    }

    private fun computeCurrentStreak(activity: Map<LocalDate, Int>, zone: ZoneOffset): Int {
        if (activity.isEmpty()) return 0
        var anchor = LocalDate.now(zone)
        if ((activity[anchor] ?: 0) == 0) {
            anchor = anchor.minusDays(1)
        }
        var streak = 0
        var d = anchor
        while ((activity[d] ?: 0) > 0) {
            streak++
            d = d.minusDays(1)
        }
        return streak
    }

    private fun longestStreakDays(activity: Map<LocalDate, Int>): Int {
        val activeDays = activity.filterValues { it > 0 }.keys.sorted()
        if (activeDays.isEmpty()) return 0
        var best = 1
        var cur = 1
        for (i in 1 until activeDays.size) {
            val prev = activeDays[i - 1]
            val day = activeDays[i]
            if (day == prev.plusDays(1)) {
                cur++
            } else {
                best = maxOf(best, cur)
                cur = 1
            }
        }
        return maxOf(best, cur)
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        private val Json = Json { ignoreUnknownKeys = true }

        private const val USER_STATS_WITH_TAGS_QUERY = """
            query UserStatsWithTags(${'$'}username: String!) {
              matchedUser(username: ${'$'}username) {
                username
                profile {
                  ranking
                  reputation
                }
                submitStatsGlobal {
                  acSubmissionNum {
                    difficulty
                    count
                    submissions
                  }
                }
                tagProblemCounts {
                  advanced { tagName tagSlug problemsSolved }
                  intermediate { tagName tagSlug problemsSolved }
                  fundamental { tagName tagSlug problemsSolved }
                }
              }
            }
        """

        private const val USER_STATS_MIN_QUERY = """
            query UserStatsMin(${'$'}username: String!) {
              matchedUser(username: ${'$'}username) {
                username
                profile {
                  ranking
                  reputation
                }
                submitStatsGlobal {
                  acSubmissionNum {
                    difficulty
                    count
                    submissions
                  }
                }
              }
            }
        """

        private const val USER_CALENDAR_QUERY = """
            query UserCalendar(${'$'}username: String!, ${'$'}year: Int!) {
              matchedUser(username: ${'$'}username) {
                userCalendar(year: ${'$'}year) {
                  streak
                  totalActiveDays
                  dccStreak
                  submissionCalendar
                }
              }
            }
        """
    }
}

class LeetCodeClientException(message: String) : RuntimeException(message)

data class LeetCodeUserSnapshot(
    val username: String,
    val totalSolved: Int,
    val easySolved: Int,
    val mediumSolved: Int,
    val hardSolved: Int,
    val ranking: Int?,
    val reputation: Double?,
    val tagSolvedBySlug: Map<String, Int>,
    val leetcodeCalendarStreak: Int?,
    val activityByUtcDate: Map<LocalDate, Int>,
    val computedDailyStreak: Int,
    val longestStreakDays: Int,
)

@Serializable
private data class GraphqlHttpBody(
    val query: String,
    val variables: JsonObject = JsonObject(emptyMap()),
)

@Serializable
private data class GraphqlResponse(
    val data: JsonObject? = null,
    val errors: List<GraphqlError>? = null,
)

@Serializable
private data class GraphqlError(
    val message: String,
)
