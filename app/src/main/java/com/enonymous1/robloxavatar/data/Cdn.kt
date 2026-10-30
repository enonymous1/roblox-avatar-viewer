package com.enonymous1.robloxavatar.data

/**
 * Resolves an `mtl`, `obj`, or texture hash from the avatar-3d JSON
 * document to its rbxcdn.com URL, using the hash algorithm from the
 * original code: start at 31, XOR every character, shard by the result
 * modulo 8.
 *
 * Returns null for a blank hash or one that could escape the host path
 * (`/`, `\`, `..`), instead of building a URL from untrusted input.
 * Verified against the documented example: hash
 * `bbdb80c2b573bf222da3e92f5f148330` resolves to `t5`. */
object Cdn {
    fun urlFor(hash: String): String? {
        if (hash.isBlank()) return null
        if ('/' in hash || '\\' in hash || ".." in hash) return null
        var shardSeed = 31
        for (ch in hash) {
            shardSeed = shardSeed xor ch.code
        }
        val shard = Math.floorMod(shardSeed, 8)
        return "https://t$shard.rbxcdn.com/$hash"
    }
}
