package maryk.foundationdb.async

interface CloseableAsyncIterator<T> : AsyncIterator<T> {
    fun close()
}
