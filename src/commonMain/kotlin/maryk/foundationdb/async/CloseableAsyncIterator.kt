package maryk.foundationdb.async

expect class CloseableAsyncIterator<T> : AsyncIterator<T> {
    fun close()
}
