package maryk.foundationdb.async

import maryk.foundationdb.FdbFuture

interface AsyncIterator<T> {
    fun hasNext(): Boolean
    suspend fun next(): T
    fun onHasNext(): FdbFuture<Boolean>
    fun cancel()
}
