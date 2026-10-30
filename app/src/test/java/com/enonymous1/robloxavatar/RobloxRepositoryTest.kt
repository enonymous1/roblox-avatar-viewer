package com.enonymous1.robloxavatar

import com.enonymous1.robloxavatar.data.AvatarResult
import com.enonymous1.robloxavatar.data.RobloxRepository
import com.enonymous1.robloxavatar.data.RobloxUser
import com.enonymous1.robloxavatar.data.UserResolution
import com.enonymous1.robloxavatar.data.api.RobloxApiFactory
import com.enonymous1.robloxavatar.data.api.ThumbnailsApi
import com.enonymous1.robloxavatar.data.api.UsersApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Repository tests against a local MockWebServer, so the Retrofit
 * wiring, the error-envelope handling, and the CDN mapping are
 * exercised with real HTTP instead of hand-written fakes. */
class RobloxRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: RobloxRepository

    private val avatarDocument = """
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
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(
                RobloxApiFactory.json.asConverterFactory("application/json".toMediaType()),
            )
            .build()
        repository = RobloxRepository(
            usersApi = retrofit.create(UsersApi::class.java),
            thumbnailsApi = retrofit.create(ThumbnailsApi::class.java),
            json = RobloxApiFactory.json,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse().setResponseCode(200).setBody(body)
            .addHeader("Content-Type", "application/json")

    private fun userJson(id: Long = 123, name: String = "ExampleUser"): String =
        """{"data":[{"id":$id,"name":"$name","displayName":"Example User","hasVerifiedBadge":true}]}"""

    @Test
    fun exactMatchThenAvatarLoadsWithCdnUrls() = runTest {
        server.enqueue(jsonResponse(userJson()))
        server.enqueue(
            jsonResponse(
                """{"data":[{"targetId":123,"state":"Completed","imageUrl":"${server.url("/model.json")}","version":"1"}]}""",
            ),
        )
        server.enqueue(jsonResponse(avatarDocument))

        val resolution = repository.resolveUser("ExampleUser")
        assertTrue(resolution is UserResolution.Single)
        val user = (resolution as UserResolution.Single).user
        assertEquals(123L, user.id)

        val result = repository.fetchAvatar(user)
        assertTrue(result is AvatarResult.Loaded)
        val avatar = (result as AvatarResult.Loaded).avatar
        assertEquals("https://t0.rbxcdn.com/f70734be44332e058a3270e4545e2943", avatar.mtlUrl)
        assertEquals("https://t5.rbxcdn.com/bbdb80c2b573bf222da3e92f5f148330", avatar.objUrl)
        assertEquals(
            listOf("https://t2.rbxcdn.com/024f209b987e47c0f2a51d3f615ab806"),
            avatar.textureUrls,
        )
        assertEquals(70.0, avatar.fov, 0.0)

        val post = server.takeRequest()
        assertEquals("/v1/usernames/users", post.path)
        assertTrue(post.body.readUtf8().contains("ExampleUser"))
        val thumbnailRequest = server.takeRequest()
        assertTrue(thumbnailRequest.path!!.startsWith("/v1/users/avatar-3d"))
    }

    @Test
    fun emptyExactMatchFallsBackToSearchWithLimit10() = runTest {
        server.enqueue(jsonResponse("""{"data":[]}"""))
        server.enqueue(
            jsonResponse(
                """{"data":[
                {"id":1,"name":"ExampleOne","displayName":"One","hasVerifiedBadge":false,"previousUsernames":[]},
                {"id":2,"name":"ExampleTwo","displayName":"Two","hasVerifiedBadge":false,"previousUsernames":[]}
                ]}""",
            ),
        )
        val resolution = repository.resolveUser("Example")
        assertTrue(resolution is UserResolution.Multiple)
        assertEquals(2, (resolution as UserResolution.Multiple).users.size)
        server.takeRequest()
        val searchRequest = server.takeRequest()
        assertTrue(searchRequest.path!!.contains("limit=10"))
    }

    @Test
    fun emptySearchIsNotFound() = runTest {
        server.enqueue(jsonResponse("""{"data":[]}"""))
        server.enqueue(jsonResponse("""{"data":[]}"""))
        assertTrue(repository.resolveUser("NobodyHere") is UserResolution.NotFound)
    }

    @Test
    fun pendingThumbnailIsPendingNotAnError() = runTest {
        server.enqueue(jsonResponse(userJson()))
        server.enqueue(
            jsonResponse("""{"data":[{"targetId":123,"state":"Pending"}]}"""),
        )
        val resolution = repository.resolveUser("ExampleUser") as UserResolution.Single
        assertEquals(
            AvatarResult.Pending,
            repository.fetchAvatar(resolution.user),
        )
    }

    @Test
    fun apiErrorEnvelopeMessageSurfaces() = runTest {
        server.enqueue(jsonResponse(userJson()))
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"errors":[{"code":0,"message":"Invalid authentication data provided"}]}""")
                .addHeader("Content-Type", "application/json"),
        )
        val resolution = repository.resolveUser("ExampleUser") as UserResolution.Single
        val result = repository.fetchAvatar(resolution.user)
        assertTrue(result is AvatarResult.Failed)
        assertEquals(
            "Invalid authentication data provided",
            (result as AvatarResult.Failed).message,
        )
    }

    @Test
    fun unusableModelHashAndBadFovFailInsteadOfRendering() = runTest {
        val user = RobloxUser(123, "ExampleUser", "Example User", false, emptyList())
        server.enqueue(
            jsonResponse(
                """{"data":[{"targetId":123,"state":"Completed","imageUrl":"${server.url("/model.json")}","version":"1"}]}""",
            ),
        )
        server.enqueue(jsonResponse(avatarDocument.replace("f70734be44332e058a3270e4545e2943", "bad/hash")))
        assertTrue(repository.fetchAvatar(user) is AvatarResult.Failed)

        server.enqueue(
            jsonResponse(
                """{"data":[{"targetId":123,"state":"Completed","imageUrl":"${server.url("/model.json")}","version":"1"}]}""",
            ),
        )
        server.enqueue(jsonResponse(avatarDocument.replace("\"fov\": 70", "\"fov\": 0")))
        assertTrue(repository.fetchAvatar(user) is AvatarResult.Failed)
    }
}
