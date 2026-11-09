package maryk.foundationdb.tuple

import java.util.List as JavaList

import com.apple.foundationdb.tuple.Versionstamp as JavaVersionstamp

import com.apple.foundationdb.tuple.Tuple as JavaTuple

import java.util.UUID
import java.math.BigInteger
import maryk.foundationdb.Range
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

actual class Tuple internal constructor(
    internal val delegate: JavaTuple
) : Comparable<Tuple>, Iterable<Any?> {
    actual constructor() : this(JavaTuple())

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

    actual fun getBigInteger(index: Int): TupleInteger = delegate.getBigInteger(index).toTupleInteger()

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
        delegate.getUUID(index).toKotlinUuid()

    actual fun addObject(value: Any?): Tuple = Tuple(delegate.addObject(convertTupleValue(value)))
    actual fun add(value: Any?): Tuple = addObject(value)
    actual fun add(value: ByteArray, offset: Int, length: Int): Tuple = Tuple(delegate.add(value, offset, length))
    actual fun addAll(values: List<Any?>): Tuple = Tuple(delegate.addAll(convertTupleValues(values)))
    actual fun addAll(tuple: Tuple): Tuple = Tuple(delegate.addAll(tuple.delegate))
    actual fun pack(prefix: ByteArray): ByteArray = delegate.pack(prefix)
    actual fun packWithVersionstamp(prefix: ByteArray): ByteArray = delegate.packWithVersionstamp(prefix)
    actual fun range(prefix: ByteArray): Range = Range(delegate.range(prefix))
    actual fun isEmpty(): Boolean = delegate.isEmpty
    actual fun getNestedTuple(index: Int): Tuple? = delegate.getNestedTuple(index)?.let(::Tuple)
    actual fun getNestedList(index: Int): List<Any?>? = delegate.getNestedList(index)?.let(::wrapValues)
    actual fun popFront(): Tuple = Tuple(delegate.popFront())
    actual fun popBack(): Tuple = Tuple(delegate.popBack())
    actual fun hasIncompleteVersionstamp(): Boolean = delegate.hasIncompleteVersionstamp()
    actual fun getPackedSize(): Int = delegate.packedSize
    actual override fun compareTo(other: Tuple): Int = delegate.compareTo(other.delegate)
    actual override fun iterator(): Iterator<Any?> = items.iterator()

    actual override fun equals(other: Any?): Boolean = other is Tuple && delegate == other.delegate
    actual override fun hashCode(): Int = delegate.hashCode()

    actual companion object {
        actual fun from(vararg elements: Any?): Tuple =
            Tuple(
                JavaTuple.from(
                    *convertTupleValues(elements.toList()).toTypedArray()
                )
            )

        actual fun fromItems(elements: Iterable<Any?>): Tuple = fromList(elements.toList())
        actual fun fromBytes(bytes: ByteArray, offset: Int, length: Int): Tuple {
            require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
            return Tuple(JavaTuple.fromBytes(bytes, offset, length))
        }
        actual fun fromList(elements: List<Any?>): Tuple =
            Tuple(JavaTuple.fromList(convertTupleValues(elements)))

        actual fun fromBytes(bytes: ByteArray): Tuple =
            Tuple(JavaTuple.fromBytes(bytes))

    }
}

@OptIn(ExperimentalUuidApi::class)
private fun convertTupleValues(values: List<Any?>): List<Any?> = values.map(::convertTupleValue)

@OptIn(ExperimentalUuidApi::class)
internal fun convertTupleValue(value: Any?): Any? = when (value) {
    is TupleInteger -> value.toJavaBigInteger()
    is Tuple -> value.delegate
    is Uuid -> value.toJavaUuid()
    is Versionstamp -> value.delegate
    is List<*> -> convertTupleValues(value)
    else -> value
}

private fun wrapValues(values: List<Any?>): List<Any?> =
    values.map(::wrapValue)

@OptIn(ExperimentalUuidApi::class)
private fun wrapValue(value: Any?): Any? = when (value) {
    is BigInteger -> value.toTupleInteger()
    is JavaTuple -> Tuple(value)
    is JavaList<*> -> Tuple.fromList(wrapValues(value as List<Any?>))
    is JavaVersionstamp -> Versionstamp(value)
    is UUID -> value.toKotlinUuid()
    else -> value
}
