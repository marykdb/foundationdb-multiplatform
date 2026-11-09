package maryk.foundationdb.tuple

import maryk.foundationdb.Range
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

expect class Tuple : Comparable<Tuple>, Iterable<Any?> {
    constructor()
    val items: List<Any?>
    val size: Int

    fun pack(): ByteArray
    fun packWithVersionstamp(): ByteArray
    fun range(): Range
    operator fun get(index: Int): Any?
    fun getBytes(index: Int): ByteArray
    fun getString(index: Int): String
    fun getBigInteger(index: Int): TupleInteger
    fun getLong(index: Int): Long
    fun getDouble(index: Int): Double
    fun getFloat(index: Int): Float
    fun getBoolean(index: Int): Boolean
    fun getTuple(index: Int): Tuple
    fun getVersionstamp(index: Int): Versionstamp
    @OptIn(ExperimentalUuidApi::class)
    fun getUuid(index: Int): Uuid

    fun addObject(value: Any?): Tuple
    fun add(value: Any?): Tuple
    fun add(value: ByteArray, offset: Int, length: Int): Tuple
    fun addAll(values: List<Any?>): Tuple
    fun addAll(tuple: Tuple): Tuple
    fun pack(prefix: ByteArray): ByteArray
    fun packWithVersionstamp(prefix: ByteArray): ByteArray
    fun range(prefix: ByteArray): Range
    fun isEmpty(): Boolean
    fun getNestedTuple(index: Int): Tuple?
    fun getNestedList(index: Int): List<Any?>?
    fun popFront(): Tuple
    fun popBack(): Tuple
    fun hasIncompleteVersionstamp(): Boolean
    fun getPackedSize(): Int
    override fun compareTo(other: Tuple): Int
    override fun iterator(): Iterator<Any?>

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    companion object {
        fun from(vararg elements: Any?): Tuple
        fun fromItems(elements: Iterable<Any?>): Tuple
        fun fromBytes(bytes: ByteArray, offset: Int, length: Int): Tuple
        fun fromList(elements: List<Any?>): Tuple
        fun fromBytes(bytes: ByteArray): Tuple
    }
}
