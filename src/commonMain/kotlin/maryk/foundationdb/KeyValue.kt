package maryk.foundationdb

expect class KeyValue(key: ByteArray, value: ByteArray) {
    val key: ByteArray
    val value: ByteArray
}
