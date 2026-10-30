package com.enonymous1.robloxavatar.data

import com.enonymous1.robloxavatar.data.api.AvatarDataDto
import com.enonymous1.robloxavatar.data.api.ErrorEnvelope
import com.enonymous1.robloxavatar.data.api.ThumbnailsApi
import com.enonymous1.robloxavatar.data.api.UserDto
import com.enonymous1.robloxavatar.data.api.UsernamesRequest
import com.enonymous1.robloxavatar.data.api.UsersApi
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

/**
 * The only place that talks to Roblox. Every call goes through [execute],
 * which turns an HTTP error into a [RobloxApiException] carrying the
 * message from the API's `{"errors": [...]}` envelope, and network or
 * parse failures into the same type, so callers never see a bare null.
 *
 * Live-shape note (2026-10-30, unauthenticated browser check): the users
 * search endpoint answered a bad `limit` with the errors envelope
 * (`Allowed values: 10, 25, 50, 100`), and the avatar-3d endpoint answered
 * with `Invalid authentication data provided`. The success shapes below
 * are the documented ones (see README); a live on-device run is still the
 * gate that confirms avatar-3d works unauthenticated from the app.
 */
class RobloxRepository(
    private val usersApi: UsersApi,
    private val thumbnailsApi: ThumbnailsApi,
    private val json: Json,
) : RobloxGateway {
    companion object {
        /** The only limits the search endpoint accepts, per its own error
         * message. The original code already used 10. */
        const val SEARCH_LIMIT = 10
    }

    /** Exact username match first; fall back to the keyword search, whose
     * results the user picks from when there is more than one. */
    override suspend fun resolveUser(rawUsername: String): UserResolution {
        val username = rawUsername.trim()
        if (username.isBlank()) return UserResolution.Failed("Enter a Roblox username.")
        return try {
            val exact = execute {
                usersApi.usersByUsernames(UsernamesRequest(listOf(username), excludeBannedUsers = true))
            }
            val matched = exact.data.firstOrNull()?.toDomain()
            if (matched != null) return UserResolution.Single(matched)

            val searched = execute { usersApi.searchUsers(username, SEARCH_LIMIT) }
            val users = searched.data.map { it.toDomain() }
            when {
                users.isEmpty() -> UserResolution.NotFound
                users.size == 1 -> UserResolution.Single(users.single())
                else -> UserResolution.Multiple(users)
            }
        } catch (e: RobloxApiException) {
            UserResolution.Failed(e.message ?: "Roblox request failed.")
        }
    }

    /** Fetch the 3D thumbnail, then the model document it points at. */
    override suspend fun fetchAvatar(user: RobloxUser): AvatarResult {
        return try {
            val thumbnails = execute { thumbnailsApi.avatar3d(user.id) }
            val thumbnail = thumbnails.data.firstOrNull()
                ?: return AvatarResult.Failed("Roblox returned no thumbnail for this user.")
            when (thumbnail.state) {
                "Pending", "InReview" -> return AvatarResult.Pending
                "Blocked" -> return AvatarResult.Unavailable
                "Completed" -> Unit
                else -> return AvatarResult.Failed("Unexpected thumbnail state: ${thumbnail.state}")
            }
            val imageUrl = thumbnail.imageUrl
                ?: return AvatarResult.Failed("Roblox returned no model for this user.")
            val data = execute { thumbnailsApi.avatarData(imageUrl) }
            AvatarResult.Loaded(data.toModel(user))
        } catch (e: RobloxApiException) {
            AvatarResult.Failed(e.message ?: "Roblox request failed.")
        } catch (e: IllegalArgumentException) {
            AvatarResult.Failed(e.message ?: "Roblox returned an unusable avatar.")
        }
    }

    private fun UserDto.toDomain(): RobloxUser = RobloxUser(
        id = id,
        name = name,
        displayName = displayName,
        hasVerifiedBadge = hasVerifiedBadge,
        previousUsernames = previousUsernames,
    )

    /** Validate the model document and resolve every hash to a CDN URL.
     * A non-finite coordinate, a non-positive field of view, an inverted
     * bounding box, or an unusable `mtl`/`obj` hash is a failure here,
     * not a broken scene later. */
    private fun AvatarDataDto.toModel(user: RobloxUser): AvatarModel {
        fun requireFinite(value: Double, label: String) {
            require(value.isFinite()) { "Avatar $label is not a finite number." }
        }
        val position = camera.position.toVector3("camera position")
        val direction = camera.direction.toVector3("camera direction")
        val min = aabb.min.toVector3("bounding-box min")
        val max = aabb.max.toVector3("bounding-box max")
        listOf(position, direction, min, max).forEach { vector ->
            requireFinite(vector.x, "coordinate")
            requireFinite(vector.y, "coordinate")
            requireFinite(vector.z, "coordinate")
        }
        require(camera.fov > 0.0 && camera.fov.isFinite()) { "Avatar field of view must be positive." }
        require(min.x <= max.x && min.y <= max.y && min.z <= max.z) {
            "Avatar bounding box is inverted."
        }
        val mtlUrl = Cdn.urlFor(mtl)
            ?: throw IllegalArgumentException("Avatar material hash is unusable.")
        val objUrl = Cdn.urlFor(obj)
            ?: throw IllegalArgumentException("Avatar geometry hash is unusable.")
        return AvatarModel(
            user = user,
            cameraPosition = position,
            cameraDirection = direction,
            fov = camera.fov,
            aabbMin = min,
            aabbMax = max,
            mtlUrl = mtlUrl,
            objUrl = objUrl,
            textureUrls = textures.mapNotNull { Cdn.urlFor(it) },
        )
    }

    private fun com.enonymous1.robloxavatar.data.api.Vec3.toVector3(label: String): Vector3 =
        Vector3(x, y, z).also {
            require(x.isFinite() && y.isFinite() && z.isFinite()) { "Avatar $label is not finite." }
        }

    private suspend fun <T> execute(call: suspend () -> Response<T>): T {
        val response = try {
            call()
        } catch (e: IOException) {
            throw RobloxApiException("Could not reach Roblox. Check your connection and try again.")
        } catch (e: Exception) {
            // Serialization failures land here: a changed response shape is
            // reported, not silently turned into an empty result.
            throw RobloxApiException("Could not read the Roblox response: ${e.message}")
        }
        if (response.isSuccessful) {
            return response.body()
                ?: throw RobloxApiException("Roblox returned an empty response.")
        }
        throw RobloxApiException(errorMessage(response))
    }

    private fun errorMessage(response: Response<*>): String {
        val rawBody = try {
            response.errorBody()?.string()
        } catch (e: IOException) {
            null
        }
        if (!rawBody.isNullOrBlank()) {
            try {
                val envelope = json.decodeFromString<ErrorEnvelope>(rawBody)
                val first = envelope.errors.firstOrNull()
                if (first != null) {
                    val field = first.field?.let { " ($it)" } ?: ""
                    return "${first.message}$field"
                }
            } catch (e: Exception) {
                // Fall through to the generic message; the raw body was not
                // the documented envelope.
            }
        }
        return "Roblox request failed (HTTP ${response.code()})."
    }
}
