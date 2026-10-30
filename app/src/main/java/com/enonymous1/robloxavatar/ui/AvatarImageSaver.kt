package com.enonymous1.robloxavatar.ui

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Turns the PNG data URL the scene hands back into a shareable file.
 * No storage permission is needed: sharing uses the app cache through a
 * FileProvider, and saving to Pictures uses MediaStore on API 29+.
 */
object AvatarImageSaver {
    private const val DATA_URL_PREFIX = "data:image/png;base64,"
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    /** Decode a `data:image/png;base64,...` URL, or return null if it is
     * not one. The PNG signature check rejects anything that is not
     * actually a PNG before it is ever written or shared. */
    fun parsePngDataUrl(dataUrl: String): ByteArray? {
        if (!dataUrl.startsWith(DATA_URL_PREFIX)) return null
        val encoded = dataUrl.substring(DATA_URL_PREFIX.length)
        if (encoded.isBlank()) return null
        val bytes = decodeBase64(encoded) ?: return null
        if (bytes.size < PNG_SIGNATURE.size) return null
        if (!bytes.copyOf(PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)) return null
        return bytes
    }

    /**
     * A small strict Base64 decoder, kept dependency-free so this function
     * is testable on the JVM and works on every supported API level
     * (java.util.Base64 needs API 26; this app supports 24).
     * Returns null for invalid input instead of throwing. */
    fun decodeBase64(input: String): ByteArray? {
        val clean = input.filterNot { it.isWhitespace() }
        // Base64 output is always a multiple of 4 characters; anything
        // else is malformed, even if its trailing bits happen to be zero.
        if (clean.isEmpty() || clean.length % 4 != 0) return null
        val padding = clean.takeLastWhile { it == '=' }.length
        if (padding > 2) return null
        val body = clean.dropLast(padding)
        if (body.any { it !in BASE64_ALPHABET }) return null
        val bytes = ArrayList<Byte>()
        var buffer = 0
        var bits = 0
        for (ch in body) {
            buffer = (buffer shl 6) or BASE64_ALPHABET.indexOf(ch)
            bits += 6
            if (bits >= 8) {
                bits -= 8
                bytes.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        if (bits > 0 && (buffer and ((1 shl bits) - 1)) != 0) return null
        return bytes.toByteArray()
    }

    private const val BASE64_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** Write the capture to the app cache and return a FileProvider URI
     * the share sheet can read. */
    suspend fun saveToCache(context: Context, pngBytes: ByteArray, fileName: String): Uri =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "roblox_avatar").apply { mkdirs() }
            val file = File(dir, fileName)
            FileOutputStream(file).use { it.write(pngBytes) }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }

    /** Save the capture under Pictures/Roblox_Avatar (API 29+). Returns
     * null on older devices, where the cache file is the only option. */
    suspend fun saveToPictures(context: Context, pngBytes: ByteArray, fileName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/Roblox_Avatar",
                )
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null
            try {
                resolver.openOutputStream(uri)?.use { it.write(pngBytes) }
                    ?: return@withContext null
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                return@withContext null
            }
            uri
        }
    }

    fun captureFileName(userId: Long): String =
        "roblox-avatar-$userId-${System.currentTimeMillis()}.png"
}
