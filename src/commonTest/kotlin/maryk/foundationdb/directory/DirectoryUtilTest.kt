package maryk.foundationdb.directory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DirectoryUtilTest {
    @Test
    fun pathJoinAndExtendWork() {
        val base = listOf("root", "parent")
        val child = listOf("child", "leaf")
        assertEquals(listOf("root", "parent", "child", "leaf"), PathUtil.join(base, child))
        assertEquals(listOf("root", "parent", "grand", "child"), PathUtil.extend(base, "grand", "child"))
        assertEquals(listOf("solo"), PathUtil.from("solo"))
    }

    @Test
    fun popFrontAndBackRequireElements() {
        val path = listOf("a", "b", "c")
        assertEquals(listOf("b", "c"), PathUtil.popFront(path))
        assertEquals(listOf("a", "b"), PathUtil.popBack(path))
        assertFailsWith<IllegalArgumentException> { PathUtil.popFront(emptyList()) }
        assertFailsWith<IllegalArgumentException> { PathUtil.popBack(emptyList()) }
    }

    @Test
    fun directoryPathStringFormatting() {
        val formatted = DirectoryUtil.pathStr(listOf("alpha", "beta"))
        assertEquals("(alpha, beta)", formatted)
        assertEquals("null", DirectoryUtil.pathStr(null))
    }
}
