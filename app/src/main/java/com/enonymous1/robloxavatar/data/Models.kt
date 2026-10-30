package com.enonymous1.robloxavatar.data

data class Vector3(
    val x: Double,
    val y: Double,
    val z: Double,
)

data class RobloxUser(
    val id: Long,
    val name: String,
    val displayName: String,
    val hasVerifiedBadge: Boolean,
    val previousUsernames: List<String>,
)

/** A fully resolved avatar, ready for the WebView scene: every URL is a
 * CDN URL and every coordinate has passed the finiteness checks in
 * [RobloxRepository]. */
data class AvatarModel(
    val user: RobloxUser,
    val cameraPosition: Vector3,
    val cameraDirection: Vector3,
    val fov: Double,
    val aabbMin: Vector3,
    val aabbMax: Vector3,
    val mtlUrl: String,
    val objUrl: String,
    val textureUrls: List<String>,
)

sealed interface UserResolution {
    data class Single(val user: RobloxUser) : UserResolution
    data class Multiple(val users: List<RobloxUser>) : UserResolution
    data object NotFound : UserResolution
    data class Failed(val message: String) : UserResolution
}

sealed interface AvatarResult {
    data class Loaded(val avatar: AvatarModel) : AvatarResult
    /** The avatar was updated recently; Roblox needs time to render it. */
    data object Pending : AvatarResult
    /** Roblox reports the thumbnail as blocked; there is no model to show. */
    data object Unavailable : AvatarResult
    data class Failed(val message: String) : AvatarResult
}

/** A Roblox API failure with the message from the API's error envelope
 * when one was available, so the UI can show what actually went wrong
 * instead of a blank screen. */
class RobloxApiException(message: String) : Exception(message)
