package maryk.foundationdb

expect object ByteArrayUtil {
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
    fun interludeJoin(interlude: ByteArray?, parts: Array<out ByteArray>): ByteArray
    fun split(src: ByteArray, delimiter: ByteArray): List<ByteArray>
    fun split(src: ByteArray, offset: Int, length: Int, delimiter: ByteArray): List<ByteArray>
    fun compareUnsigned(left: ByteArray, right: ByteArray): Int
    fun comparator(): Comparator<ByteArray>
    fun startsWith(src: ByteArray, prefix: ByteArray): Boolean
    fun strinc(key: ByteArray): ByteArray
    fun keyAfter(key: ByteArray): ByteArray
    fun encodeInt(value: Long): ByteArray
    fun decodeInt(value: ByteArray): Long

}
