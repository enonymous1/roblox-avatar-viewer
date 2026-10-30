package com.enonymous1.robloxavatar

import com.enonymous1.robloxavatar.data.api.AvatarDataDto
import com.enonymous1.robloxavatar.data.api.ErrorEnvelope
import com.enonymous1.robloxavatar.data.api.RobloxApiFactory
import com.enonymous1.robloxavatar.data.api.ThumbnailsResponse
import com.enonymous1.robloxavatar.data.api.UsersResponse
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Fixtures use the documented response shapes plus fictional users.
 * The avatar-3d document fixture is the documented example (camera,
 * aabb, mtl, obj, textures); the thumbnail and user fixtures follow the
 * `data` wrapper both endpoints return. */
class DtoParsingTest {

    private val json = RobloxApiFactory.json

    @Test
    fun avatarDataDocumentParses() {
        val parsed = json.decodeFromString<AvatarDataDto>(
            """
            {
              "camera": {
                "position": {"x": -2.1652, "y": 104.947, "z": -4.72907},
                "direction": {"x": -0.40558, "y": 0.40558, "z": -0.819152},
                "fov": 70
              },
              "aabb": {
                "min": {"x": -2.03532, "y": 100.003, "z": -1.16793},
                "max": {"x": 2.24066, "y": 105.39, "z": 0.622441}
              },
              "mtl": "f70734be44332e058a3270e4545e2943",
              "obj": "bbdb80c2b573bf222da3e92f5f148330",
              "textures": ["024f209b987e47c0f2a51d3f615ab806"]
            }
            """.trimIndent(),
        )
        assertEquals(70.0, parsed.camera.fov, 0.0)
        assertEquals(104.947, parsed.camera.position.y, 0.0)
        assertEquals("f70734be44332e058a3270e4545e2943", parsed.mtl)
        assertEquals("bbdb80c2b573bf222da3e92f5f148330", parsed.obj)
        assertEquals(listOf("024f209b987e47c0f2a51d3f615ab806"), parsed.textures)
    }

    @Test
    fun thumbnailWrapperParsesAndPendingHasNoImageUrl() {
        val completed = json.decodeFromString<ThumbnailsResponse>(
            """{"data":[{"targetId":123,"state":"Completed","imageUrl":"https://example.invalid/avatar.json","version":"1"}]}""",
        )
        assertEquals("Completed", completed.data.single().state)
        assertEquals("https://example.invalid/avatar.json", completed.data.single().imageUrl)

        val pending = json.decodeFromString<ThumbnailsResponse>(
            """{"data":[{"targetId":123,"state":"Pending"}]}""",
        )
        assertEquals("Pending", pending.data.single().state)
        assertNull(pending.data.single().imageUrl)
    }

    @Test
    fun userSearchKeepsPreviousUsernamesAndUsernamesEndpointDefaultsThem() {
        val searched = json.decodeFromString<UsersResponse>(
            """
            {"data":[{"id":123,"name":"ExampleUser","displayName":"Example User",
            "hasVerifiedBadge":true,"previousUsernames":["OldName"]}]}
            """.trimIndent(),
        )
        val user = searched.data.single()
        assertTrue(user.hasVerifiedBadge)
        assertEquals(listOf("OldName"), user.previousUsernames)

        val exact = json.decodeFromString<UsersResponse>(
            """{"data":[{"id":123,"name":"ExampleUser","displayName":"Example User","hasVerifiedBadge":false}]}""",
        )
        assertEquals(emptyList<String>(), exact.data.single().previousUsernames)
    }

    @Test
    fun errorEnvelopeParses() {
        val envelope = json.decodeFromString<ErrorEnvelope>(
            """{"errors":[{"code":0,"message":"Invalid authentication data provided"}]}""",
        )
        assertEquals("Invalid authentication data provided", envelope.errors.single().message)
    }

    @Test
    fun missingRequiredAvatarFieldFailsLoudly() {
        try {
            json.decodeFromString<AvatarDataDto>(
                """{"camera":{"position":{"x":0,"y":0,"z":0},"direction":{"x":0,"y":0,"z":0},"fov":70},
                "aabb":{"min":{"x":0,"y":0,"z":0},"max":{"x":1,"y":1,"z":1}},
                "mtl":"f70734be44332e058a3270e4545e2943","textures":[]}""",
            )
            fail("Expected a SerializationException for the missing obj field")
        } catch (e: SerializationException) {
            // Expected: a changed or truncated model document must not
            // silently decode into a broken scene.
        }
    }
}
