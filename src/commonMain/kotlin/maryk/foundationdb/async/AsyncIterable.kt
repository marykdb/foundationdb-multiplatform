package maryk.foundationdb.async

import maryk.foundationdb.FdbFuture

interface AsyncIterable<T> {
    fun iterator(): AsyncIterator<T>
    fun asList(): FdbFuture<List<T>>
}
