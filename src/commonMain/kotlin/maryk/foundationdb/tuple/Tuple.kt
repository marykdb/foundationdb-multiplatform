package maryk.foundationdb.tuple

import maryk.foundationdb.Range

expect class Tuple {
    val items: List<Any?>

    fun pack(): ByteArray
    fun packWithVersionstamp(): ByteArray
    fun range(): Range

    companion object {
        fun from(vararg elements: Any?): Tuple
        fun fromList(elements: List<Any?>): Tuple
        fun fromBytes(bytes: ByteArray): Tuple
    }
}
