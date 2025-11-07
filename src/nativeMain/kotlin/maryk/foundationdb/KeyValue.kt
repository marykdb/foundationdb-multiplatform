package maryk.foundationdb

actual class KeyValue actual constructor(key: ByteArray, value: ByteArray) {
    actual val key: ByteArray = key.copyOf()
    actual val value: ByteArray = value.copyOf()
}
