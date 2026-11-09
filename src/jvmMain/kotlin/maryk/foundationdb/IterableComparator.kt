package maryk.foundationdb

import com.apple.foundationdb.tuple.IterableComparator as JavaIterableComparator
import maryk.foundationdb.tuple.convertTupleValue

private val tupleComparator = JavaIterableComparator()

internal actual fun compareTupleIterables(a: Iterable<Any?>, b: Iterable<Any?>): Int =
    tupleComparator.compare(a.javaTupleItems(), b.javaTupleItems())

/** Convert only items actually visited by the Java comparator, not the whole iterable. */
private fun Iterable<Any?>.javaTupleItems(): Iterable<Any?> = Iterable {
    val source = iterator()
    object : Iterator<Any?> {
        override fun hasNext(): Boolean = source.hasNext()
        override fun next(): Any? = comparatorValue(source.next())
    }
}

private fun comparatorValue(value: Any?): Any? = when (value) {
    is List<*> -> object : AbstractList<Any?>() {
        override val size: Int get() = value.size
        override fun get(index: Int): Any? = comparatorValue(value[index])
        override fun iterator(): Iterator<Any?> = value.javaTupleItems().iterator()
    }
    else -> convertTupleValue(value)
}
