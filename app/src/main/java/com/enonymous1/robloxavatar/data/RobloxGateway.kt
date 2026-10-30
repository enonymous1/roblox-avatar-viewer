package com.enonymous1.robloxavatar.data

/** The data operations the ViewModel needs, behind an interface so tests
 * can substitute a fake without a web server. */
interface RobloxGateway {
    suspend fun resolveUser(rawUsername: String): UserResolution
    suspend fun fetchAvatar(user: RobloxUser): AvatarResult
}
