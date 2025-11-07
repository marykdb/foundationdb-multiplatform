package maryk.foundationdb.directory

internal object DirectoryUtil {
    fun pathStr(path: List<String>?): String =
        path?.joinToString(prefix = "(", postfix = ")") ?: "null"
}

private val HEX_CHARS = "0123456789abcdef".toCharArray()

internal fun ByteArray.toHexString(): String =
    buildString(size * 2 + 2) {
        append("0x")
        for (byte in this@toHexString) {
            val value = byte.toInt() and 0xFF
            append(HEX_CHARS[value ushr 4])
            append(HEX_CHARS[value and 0x0F])
        }
    }
