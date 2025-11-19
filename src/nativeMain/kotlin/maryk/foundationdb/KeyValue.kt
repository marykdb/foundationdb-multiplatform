package maryk.foundationdb

actual class KeyValue actual constructor(key: ByteArray, value: ByteArray) {
    actual val key: ByteArray = key.copyOf()
    actual val value: ByteArray = value.copyOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyValue) return false
        return key.contentEquals(other.key) && value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = key.contentHashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }

    override fun toString(): String =
        "KeyValue(key=${key.contentToString()}, value=${value.contentToString()})"
}
