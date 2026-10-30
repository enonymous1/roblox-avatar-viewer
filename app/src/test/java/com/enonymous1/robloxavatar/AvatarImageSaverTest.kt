package com.enonymous1.robloxavatar

import com.enonymous1.robloxavatar.ui.AvatarImageSaver
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class AvatarImageSaverTest {

    // A 1x1 transparent PNG.
    private val tinyPngBase64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8Xw8AAwMCAO+ip1sAAAAASUVORK5CYII="

    @Test
    fun validPngDataUrlDecodesAndHasPngSignature() {
        val bytes = AvatarImageSaver.parsePngDataUrl("data:image/png;base64,$tinyPngBase64")
        assertNotNull(bytes)
        assertArrayEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
            bytes!!.copyOf(8),
        )
    }

    @Test
    fun wrongPrefixBlankPayloadAndNonPngAreRejected() {
        assertNull(AvatarImageSaver.parsePngDataUrl("data:image/jpeg;base64,$tinyPngBase64"))
        assertNull(AvatarImageSaver.parsePngDataUrl("data:image/png;base64,"))
        // "hello" in Base64: valid encoding, not a PNG.
        assertNull(AvatarImageSaver.parsePngDataUrl("data:image/png;base64,aGVsbG8="))
        assertNull(AvatarImageSaver.parsePngDataUrl("data:image/png;base64,!!!not-base64!!!"))
    }

    @Test
    fun base64DecoderRoundTrips() {
        assertArrayEquals(
            "hello".toByteArray(Charsets.UTF_8),
            AvatarImageSaver.decodeBase64("aGVsbG8="),
        )
        assertNull(AvatarImageSaver.decodeBase64("abc"))
        assertNull(AvatarImageSaver.decodeBase64("a*b="))
    }
}
