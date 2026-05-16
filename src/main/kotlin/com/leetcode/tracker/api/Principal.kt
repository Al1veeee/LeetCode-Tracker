package com.leetcode.tracker.api

import io.ktor.server.auth.Principal
import java.util.UUID

data class AuthenticatedUser(
    val userId: UUID,
    val email: String,
) : Principal
