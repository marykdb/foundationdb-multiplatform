package maryk.foundationdb.tuple

import java.util.UUID
import maryk.foundationdb.Range
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

actual class Tuple internal constructor(
    internal val delegate: com.apple.foundationdb.tuple.Tuple
) {
    actual val items: List<Any?>
        get() = wrapValues(delegate.items)

    actual val size: Int
        get() = delegate.size()

    actual fun pack(): ByteArray = delegate.pack()

    actual fun packWithVersionstamp(): ByteArray =
        delegate.packWithVersionstamp()

    actual fun range(): Range = Range(delegate.range())

    actual operator fun get(index: Int): Any? = wrapValue(delegate[index])

    actual fun getBytes(index: Int): ByteArray = delegate.getBytes(index)

    actual fun getString(index: Int): String = delegate.getString(index)

    actual fun getLong(index: Int): Long = delegate.getLong(index)

    actual fun getDouble(index: Int): Double = delegate.getDouble(index)

    actual fun getFloat(index: Int): Float = delegate.getFloat(index)

    actual fun getBoolean(index: Int): Boolean = delegate.getBoolean(index)

    actual fun getTuple(index: Int): Tuple =
        Tuple(delegate.getNestedTuple(index))

    actual fun getVersionstamp(index: Int): Versionstamp =
        Versionstamp(delegate.getVersionstamp(index))

    @OptIn(ExperimentalUuidApi::class)
    actual fun getUuid(index: Int): Uuid =
        Uuid.parse(delegate.getUUID(index).toString())

    actual companion object {
        actual fun from(vararg elements: Any?): Tuple =
            Tuple(
                com.apple.foundationdb.tuple.Tuple.from(
                    *convertValues(elements.toList()).toTypedArray()
                )
            )

        actual fun fromList(elements: List<Any?>): Tuple =
            Tuple(com.apple.foundationdb.tuple.Tuple.fromList(convertValues(elements)))

        actual fun fromBytes(bytes: ByteArray): Tuple =
            Tuple(com.apple.foundationdb.tuple.Tuple.fromBytes(bytes))

    }
}

@OptIn(ExperimentalUuidApi::class)
private fun convertValues(values: List<Any?>): List<Any?> =
    values.map { value ->
        when (value) {
            is Tuple -> value.delegate
            is Uuid -> UUID.fromString(value.toString())
            is Versionstamp -> value.delegate
            is List<*> -> convertValues(value)
            else -> value
        }
    }

private fun wrapValues(values: List<Any?>): List<Any?> =
    values.map(::wrapValue)

@OptIn(ExperimentalUuidApi::class)
private fun wrapValue(value: Any?): Any? = when (value) {
    is com.apple.foundationdb.tuple.Tuple -> Tuple(value)
    is java.util.List<*> -> Tuple.fromList(wrapValues(value as List<Any?>))
    is com.apple.foundationdb.tuple.Versionstamp -> Versionstamp(value)
    is UUID -> Uuid.parse(value.toString())
    else -> value
}
