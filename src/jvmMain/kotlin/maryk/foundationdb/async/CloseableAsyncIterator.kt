package maryk.foundationdb.async

actual class CloseableAsyncIterator<T> internal constructor(
    private val closeableDelegate: com.apple.foundationdb.async.CloseableAsyncIterator<*>,
    mapper: (Any?) -> T
) : AsyncIterator<T>(closeableDelegate, mapper) {
    actual fun close() {
        closeableDelegate.close()
    }
}
