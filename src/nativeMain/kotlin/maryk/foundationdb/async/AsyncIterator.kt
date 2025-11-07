package maryk.foundationdb.async

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.completedFdbFuture

actual open class AsyncIterator<T> {
    protected open val backing: MutableList<T> = mutableListOf()
    protected var position: Int = 0

    internal fun initialise(items: List<T>) {
        backing.clear()
        backing.addAll(items)
        position = 0
    }

    actual open fun hasNext(): Boolean = position < backing.size

    actual open suspend fun next(): T {
        if (!hasNext()) throw NoSuchElementException()
        return backing[position++]
    }

    actual open fun onHasNext(): FdbFuture<Boolean> = completedFdbFuture(hasNext())

    actual open fun cancel() {
        position = backing.size
    }
}
