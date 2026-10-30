package com.enonymous1.robloxavatar

import com.enonymous1.robloxavatar.data.Cdn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CdnTest {

    @Test
    fun documentedExampleResolvesToT5() {
        // The hash used in the documented avatar-3d example resolves to
        // the t5 host in that same documentation.
        assertEquals(
            "https://t5.rbxcdn.com/bbdb80c2b573bf222da3e92f5f148330",
            Cdn.urlFor("bbdb80c2b573bf222da3e92f5f148330"),
        )
    }

    @Test
    fun otherHashesUseTheirOwnShards() {
        assertEquals(
            "https://t0.rbxcdn.com/f70734be44332e058a3270e4545e2943",
            Cdn.urlFor("f70734be44332e058a3270e4545e2943"),
        )
        assertEquals(
            "https://t2.rbxcdn.com/024f209b987e47c0f2a51d3f615ab806",
            Cdn.urlFor("024f209b987e47c0f2a51d3f615ab806"),
        )
    }

    @Test
    fun blankAndUnsafeHashesAreRejected() {
        assertNull(Cdn.urlFor(""))
        assertNull(Cdn.urlFor("   "))
        assertNull(Cdn.urlFor("abc/def"))
        assertNull(Cdn.urlFor("..\\secret"))
        assertNull(Cdn.urlFor("../escape"))
    }
}
