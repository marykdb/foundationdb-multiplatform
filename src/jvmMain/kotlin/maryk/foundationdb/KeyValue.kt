package maryk.foundationdb

import com.apple.foundationdb.KeyValue as JavaKeyValue

actual open class KeyValue internal constructor(
    internal val delegate: JavaKeyValue
) {
    actual constructor(key: ByteArray, value: ByteArray) : this(JavaKeyValue(key, value))

    internal actual constructor(key: ByteArray, value: ByteArray, copy: Boolean) : this(
        JavaKeyValue(if (copy) key.copyOf() else key, if (copy) value.copyOf() else value)
    )

    actual open val key: ByteArray
        get() = delegate.key

    actual open val value: ByteArray
        get() = delegate.value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyValue || this::class != other::class) return false
        return key.contentEquals(other.key) && value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return 17 + 37 * key.contentHashCode() + value.contentHashCode()
    }

    override fun toString(): String =
        "KeyValue(key=${delegate.key.contentToString()}, value=${delegate.value.contentToString()})"

    internal companion object {
        fun fromDelegate(delegate: JavaKeyValue): KeyValue = KeyValue(delegate)
    }
}
