package com.chore.tracker.data

class Repo(val session: Session) {
    val api: ChoreApi = ApiFactory.create(session)

    suspend fun login(email: String, password: String): AuthResponse {
        val res = api.login(LoginRequest(email, password))
        session.setToken(res.token)
        return res
    }

    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        householdName: String,
    ): AuthResponse {
        val res = api.register(RegisterRequest(email, password, displayName, householdName))
        session.setToken(res.token)
        return res
    }

    suspend fun logout() = session.setToken(null)
}
