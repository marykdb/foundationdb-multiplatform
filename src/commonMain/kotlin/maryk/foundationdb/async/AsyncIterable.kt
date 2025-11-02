package maryk.foundationdb.async

import maryk.foundationdb.FdbFuture

expect class AsyncIterable<T> {
    fun iterator(): AsyncIterator<T>
    fun asList(): FdbFuture<List<T>>
}
