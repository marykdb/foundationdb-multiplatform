package maryk.foundationdb.tuple

import maryk.foundationdb.ByteArrayUtil
import maryk.foundationdb.Range
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

actual class Tuple internal constructor(
    internal val elements: List<Any?>
) : Comparable<Tuple>, Iterable<Any?> {
    actual constructor() : this(emptyList())

    actual val items: List<Any?> get() = elements.toList()
    actual val size: Int get() = elements.size

    actual fun pack(): ByteArray = TupleCodec.pack(elements, allowIncompleteVersionstamp = false)

    actual fun packWithVersionstamp(): ByteArray =
        TupleCodec.packWithVersionstamp(elements)

    actual fun range(): Range = range(byteArrayOf())

    actual operator fun get(index: Int): Any? = elements[index]

    actual fun getBytes(index: Int): ByteArray = expectType(index, "byte array") { it as? ByteArray }

    actual fun getString(index: Int): String = expectType(index, "string") { it as? String }

    actual fun getBigInteger(index: Int): TupleInteger = when (val value = elements[index]) { is TupleInteger -> value; is Number -> TupleInteger.fromLong(value.toLong()); else -> throw ClassCastException("Tuple item is not numeric") }

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

    actual fun getTuple(index: Int): Tuple = requireNotNull(getNestedTuple(index))

    actual fun getVersionstamp(index: Int): Versionstamp = expectType(index, "Versionstamp") { it as? Versionstamp }

    @OptIn(ExperimentalUuidApi::class)
    actual fun getUuid(index: Int): Uuid = expectType(index, "Uuid") { it as? Uuid }

    actual fun addObject(value: Any?): Tuple = fromList(elements + listOf(value))
    actual fun add(value: Any?): Tuple = addObject(value)
    actual fun add(value: ByteArray, offset: Int, length: Int): Tuple {
        require(offset >= 0 && length >= 0 && offset <= value.size - length)
        return addObject(value.copyOfRange(offset, offset + length))
    }
    actual fun addAll(values: List<Any?>): Tuple = fromList(elements + values)
    actual fun addAll(tuple: Tuple): Tuple = addAll(tuple.items)
    actual fun pack(prefix: ByteArray): ByteArray = prefix + pack()
    actual fun packWithVersionstamp(prefix: ByteArray): ByteArray {
        val packed = packWithVersionstamp()
        val offsetSize = versionstampOffsetSize()
        val position = packed.size - offsetSize
        var offset = (0 until offsetSize).fold(0L) { result, index -> result or ((packed[position + index].toLong() and 255) shl (index * 8)) }
        offset += prefix.size
        require(offset <= if (offsetSize == 2) 0xffffL else Int.MAX_VALUE.toLong()) { "Versionstamp offset exceeds selected API limit" }
        for (index in 0 until offsetSize) packed[position + index] = (offset ushr (index * 8)).toByte()
        return prefix + packed
    }
    actual fun range(prefix: ByteArray): Range {
        check(!hasIncompleteVersionstamp()) { "Tuple with incomplete versionstamp used for range" }
        val packed = pack(prefix)
        return Range(packed + byteArrayOf(0), packed + byteArrayOf(0xff.toByte()))
    }
    actual fun isEmpty(): Boolean = elements.isEmpty()
    actual fun getNestedTuple(index: Int): Tuple? = when (val value = elements[index]) {
        null -> null
        is Tuple -> value
        is List<*> -> fromList(value)
        else -> throw ClassCastException("Tuple item is not nested")
    }
    actual fun getNestedList(index: Int): List<Any?>? = getNestedTuple(index)?.items
    actual fun popFront(): Tuple { check(elements.isNotEmpty()) { "Tuple contains no elements" }; return fromList(elements.drop(1)) }
    actual fun popBack(): Tuple { check(elements.isNotEmpty()) { "Tuple contains no elements" }; return fromList(elements.dropLast(1)) }
    actual fun hasIncompleteVersionstamp(): Boolean = containsIncompleteStamp(elements)
    actual fun getPackedSize(): Int {
        val count = incompleteStampCount(elements)
        return TupleCodec.getPackedSize(elements, nested = false) + if (count == 0) 0 else count * versionstampOffsetSize()
    }
    actual override fun compareTo(other: Tuple): Int = compareTupleItems(elements, other.elements)
    actual override fun iterator(): Iterator<Any?> = items.iterator()

    actual override fun equals(other: Any?): Boolean = this === other || (other is Tuple && compareTo(other) == 0)
    actual override fun hashCode(): Int = (if (hasIncompleteVersionstamp()) packWithVersionstamp() else pack()).contentHashCode()

    actual companion object {
        actual fun from(vararg elements: Any?): Tuple =
            Tuple(wrapDecoded(elements.asList()))

        actual fun fromItems(elements: Iterable<Any?>): Tuple = Tuple(wrapDecoded(elements))
        actual fun fromBytes(bytes: ByteArray, offset: Int, length: Int): Tuple {
            require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
            return Tuple(wrapDecoded(TupleCodec.unpack(bytes, offset, length)))
        }
        actual fun fromList(elements: List<Any?>): Tuple =
            Tuple(wrapDecoded(elements))

        actual fun fromBytes(bytes: ByteArray): Tuple =
            Tuple(wrapDecoded(TupleCodec.unpack(bytes)))

        private fun wrapDecoded(values: Iterable<Any?>): List<Any?> =
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

private fun containsIncompleteStamp(values: List<Any?>): Boolean = values.any { value -> when (value) {
    is Versionstamp -> !value.isComplete
    is Tuple -> value.hasIncompleteVersionstamp()
    is List<*> -> containsIncompleteStamp(value)
    else -> false
} }

private fun incompleteStampCount(values: List<Any?>): Int = values.sumOf { value -> when (value) {
    is Versionstamp -> if (value.isComplete) 0 else 1
    is Tuple -> incompleteStampCount(value.items)
    is List<*> -> incompleteStampCount(value)
    else -> 0
} }

internal fun compareTupleItems(first: Iterable<Any?>, second: Iterable<Any?>): Int {
    val firstIterator = first.iterator()
    val secondIterator = second.iterator()
    while (firstIterator.hasNext() && secondIterator.hasNext()) {
        val left = firstIterator.next()
        val right = secondIterator.next()
        val leftNested = when (left) { is Tuple -> left.elements; is List<*> -> left; else -> null }
        val rightNested = when (right) { is Tuple -> right.elements; is List<*> -> right; else -> null }
        val comparison = when {
            left is Versionstamp && right is Versionstamp -> left.compareTo(right)
            leftNested != null && rightNested != null -> compareTupleItems(leftNested, rightNested)
            else -> ByteArrayUtil.compareUnsigned(TupleCodec.pack(listOf(left), true), TupleCodec.pack(listOf(right), true))
        }
        if (comparison != 0) return comparison
    }
    return when { firstIterator.hasNext() -> 1; secondIterator.hasNext() -> -1; else -> 0 }
}
