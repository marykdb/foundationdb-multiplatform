package maryk.foundationdb

expect object StringUtil {
    fun validate(string: String)
    fun compareUtf8(a: String, b: String): Int
    fun packedSize(string: String): Int
}
