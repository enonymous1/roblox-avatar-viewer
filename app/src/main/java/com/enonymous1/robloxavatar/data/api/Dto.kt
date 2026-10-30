package com.enonymous1.robloxavatar.data.api

import kotlinx.serialization.Serializable

/** Request body for POST /v1/usernames/users. Serialized by Retrofit, so a
 * username containing quotes or backslashes cannot break the JSON the way
 * the original hand-built String.format body could. */
@Serializable
data class UsernamesRequest(
    val usernames: List<String>,
    val excludeBannedUsers: Boolean,
)

/** One user from either the usernames endpoint or the search endpoint.
 *
 * The usernames endpoint omits [previousUsernames]; the search endpoint
 * includes it. Both shapes decode into this one DTO because the field has
 * a default. */
@Serializable
data class UserDto(
    val id: Long,
    val name: String,
    val displayName: String,
    val hasVerifiedBadge: Boolean = false,
    val previousUsernames: List<String> = emptyList(),
)

/** The `data` wrapper both user endpoints return. */
@Serializable
data class UsersResponse(
    val data: List<UserDto> = emptyList(),
)

/** One entry from GET /v1/users/avatar-3d.
 *
 * [imageUrl] is absent when the thumbnail is blocked, so it is nullable.
 * [state] is one of `Completed`, `Pending`, `InReview`, or `Blocked`. */
@Serializable
data class ThumbnailDto(
    val targetId: Long,
    val state: String,
    val imageUrl: String? = null,
    val version: String? = null,
)

/** The `data` wrapper the avatar-3d endpoint returns. The original code
 * parsed the thumbnail fields at the top level and therefore never matched
 * this shape; parsing the wrapper is the fix. */
@Serializable
data class ThumbnailsResponse(
    val data: List<ThumbnailDto> = emptyList(),
)

@Serializable
data class Vec3(
    val x: Double,
    val y: Double,
    val z: Double,
)

/** Camera and bounding-box payload fetched from the thumbnail's
 * [ThumbnailDto.imageUrl]. Its shape is the one documented for the
 * avatar-3d JSON document: camera, aabb, mtl, obj, textures. */
@Serializable
data class CameraDto(
    val position: Vec3,
    val direction: Vec3,
    val fov: Double,
)

@Serializable
data class AabbDto(
    val min: Vec3,
    val max: Vec3,
)

@Serializable
data class AvatarDataDto(
    val camera: CameraDto,
    val aabb: AabbDto,
    val mtl: String,
    val obj: String,
    val textures: List<String> = emptyList(),
)

/** The error envelope Roblox returns for bad requests and, as observed on
 * 2026-10-30, for unauthenticated avatar-3d calls:
 * `{"errors":[{"code":0,"message":"..."}]}`. */
@Serializable
data class ApiError(
    val code: Int,
    val message: String,
    val field: String? = null,
)

@Serializable
data class ErrorEnvelope(
    val errors: List<ApiError> = emptyList(),
)
