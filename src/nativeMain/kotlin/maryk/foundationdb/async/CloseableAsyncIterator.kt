package maryk.foundationdb.async

actual class CloseableAsyncIterator<T> actual constructor(
    items: List<T>,
    private val closeAction: () -> Unit
) : AsyncIterator<T>() {
    init {
        initialise(items)
    }

    actual fun close() {
        closeAction()
        cancel()
    }
}
