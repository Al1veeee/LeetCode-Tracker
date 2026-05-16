package com.leetcode.tracker.application

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.time.Duration
import java.time.Instant
import java.util.UUID

class JwtService(
    secret: String,
    private val issuer: String = "leetcode-progress-tracker",
    private val audience: String = "leetcode-progress-tracker-users",
) {
    private val algorithm = Algorithm.HMAC256(secret)
    private val ttl = Duration.ofDays(14)

    fun generate(userId: UUID, email: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId.toString())
            .withClaim("email", email)
            .withExpiresAt(Instant.now().plus(ttl).let { java.util.Date.from(it) })
            .sign(algorithm)

    fun verifier(): com.auth0.jwt.interfaces.JWTVerifier =
        JWT.require(algorithm)
            .withIssuer(issuer)
            .withAudience(audience)
            .build()
}
