package maryk.foundationdb.tuple

import maryk.foundationdb.Range
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

actual class Tuple internal constructor(
    internal val elements: List<Any?>
) {
    actual val items: List<Any?> get() = elements
    actual val size: Int get() = elements.size

    actual fun pack(): ByteArray = TupleCodec.pack(elements, allowIncompleteVersionstamp = false)

    actual fun packWithVersionstamp(): ByteArray =
        TupleCodec.packWithVersionstamp(elements)

    actual fun range(): Range {
        val packed = pack()
        return Range(packed, packed + byteArrayOf(0x00))
    }

    actual operator fun get(index: Int): Any? = elements[index]

    actual fun getBytes(index: Int): ByteArray = expectType(index, "byte array") { it as? ByteArray }

    actual fun getString(index: Int): String = expectType(index, "string") { it as? String }

    actual fun getLong(index: Int): Long = expectType(index, "long") {
        when (it) {
            is Long -> it
            is Number -> it.toLong()
            else -> null
        }
    }

    actual fun getDouble(index: Int): Double = expectType(index, "double") {
        when (it) {
            is Double -> it
            is Number -> it.toDouble()
            else -> null
        }
    }

    actual fun getFloat(index: Int): Float = expectType(index, "float") {
        when (it) {
            is Float -> it
            is Number -> it.toFloat()
            else -> null
        }
    }

    actual fun getBoolean(index: Int): Boolean = expectType(index, "boolean") { it as? Boolean }

    actual fun getTuple(index: Int): Tuple = expectType(index, "tuple") { it as? Tuple }

    actual fun getVersionstamp(index: Int): Versionstamp = expectType(index, "Versionstamp") { it as? Versionstamp }

    @OptIn(ExperimentalUuidApi::class)
    actual fun getUuid(index: Int): Uuid = expectType(index, "Uuid") { it as? Uuid }

    actual companion object {
        actual fun from(vararg elements: Any?): Tuple =
            Tuple(elements.toList())

        actual fun fromList(elements: List<Any?>): Tuple =
            Tuple(elements.toList())

        actual fun fromBytes(bytes: ByteArray): Tuple =
            Tuple(wrapDecoded(TupleCodec.unpack(bytes)))

        private fun wrapDecoded(values: List<Any?>): List<Any?> =
            values.map { value ->
                when (value) {
                    is List<*> -> Tuple(wrapDecoded(value))
                    else -> value
                }
            }
    }
}

private fun <T> Tuple.expectType(index: Int, expected: String, cast: (Any?) -> T?): T {
    val value = elements[index]
    return cast(value) ?: throw IllegalArgumentException(
        "Expected $expected at index $index but found ${value?.let { it::class.simpleName } ?: "null"}"
    )
}

private operator fun ByteArray.plus(other: ByteArray): ByteArray {
    val result = ByteArray(size + other.size)
    copyInto(result, 0)
    other.copyInto(result, size)
    return result
}
