package maryk.foundationdb

internal expect object ByteArrayUtil {
    fun join(vararg parts: ByteArray): ByteArray
    fun join(interlude: ByteArray?, parts: List<ByteArray>? = null): ByteArray

    fun regionEquals(src: ByteArray?, start: Int, pattern: ByteArray?): Boolean

    fun replace(src: ByteArray?, pattern: ByteArray?, replacement: ByteArray?): ByteArray?
    fun replace(
        src: ByteArray?,
        offset: Int,
        length: Int,
        pattern: ByteArray?,
        replacement: ByteArray?
    ): ByteArray?

    fun printable(bytes: ByteArray?): String?

    fun nullCount(bytes: ByteArray): Int
}
