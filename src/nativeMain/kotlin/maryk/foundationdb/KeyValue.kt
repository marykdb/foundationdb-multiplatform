package maryk.foundationdb

actual open class KeyValue internal actual constructor(key: ByteArray, value: ByteArray, copy: Boolean) {
    actual constructor(key: ByteArray, value: ByteArray) : this(key, value, true)
    actual open val key: ByteArray = if (copy) key.copyOf() else key
    actual open val value: ByteArray = if (copy) value.copyOf() else value

    internal companion object {
        /** Only for newly allocated Kotlin arrays, never borrowed C storage or caller-owned arrays. */
        fun takeOwnership(key: ByteArray, value: ByteArray): KeyValue = KeyValue(key, value, false)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyValue || this::class != other::class) return false
        return key.contentEquals(other.key) && value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return 17 + 37 * key.contentHashCode() + value.contentHashCode()
    }

    override fun toString(): String =
        "KeyValue(key=${key.contentToString()}, value=${value.contentToString()})"
}
