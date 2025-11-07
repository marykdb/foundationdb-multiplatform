package maryk.foundationdb.tuple

import maryk.foundationdb.Range
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

expect class Tuple {
    val items: List<Any?>
    val size: Int

    fun pack(): ByteArray
    fun packWithVersionstamp(): ByteArray
    fun range(): Range
    operator fun get(index: Int): Any?
    fun getBytes(index: Int): ByteArray
    fun getString(index: Int): String
    fun getLong(index: Int): Long
    fun getDouble(index: Int): Double
    fun getFloat(index: Int): Float
    fun getBoolean(index: Int): Boolean
    fun getTuple(index: Int): Tuple
    fun getVersionstamp(index: Int): Versionstamp
    @OptIn(ExperimentalUuidApi::class)
    fun getUuid(index: Int): Uuid

    companion object {
        fun from(vararg elements: Any?): Tuple
        fun fromList(elements: List<Any?>): Tuple
        fun fromBytes(bytes: ByteArray): Tuple
    }
}
