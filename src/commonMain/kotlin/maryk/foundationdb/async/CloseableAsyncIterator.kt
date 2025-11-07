package maryk.foundationdb.async

expect class CloseableAsyncIterator<T> : AsyncIterator<T> {
    constructor(items: List<T>, closeAction: () -> Unit)
    fun close()
}

fun <T> closeableAsyncIterator(items: List<T>, closeAction: () -> Unit = {}): CloseableAsyncIterator<T> =
    CloseableAsyncIterator(items, closeAction)
