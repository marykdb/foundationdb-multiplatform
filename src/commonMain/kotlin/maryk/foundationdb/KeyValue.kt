package maryk.foundationdb

expect open class KeyValue(key: ByteArray, value: ByteArray) {
    /** Internal ownership transfer for rows whose arrays are already owned by Kotlin. */
    internal constructor(key: ByteArray, value: ByteArray, copy: Boolean)
    open val key: ByteArray
    open val value: ByteArray
}
