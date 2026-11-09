package maryk.foundationdb

/** Lexicographic FoundationDB tuple-item order, including bytes, nulls and nested values. */
class IterableComparator<T> : Comparator<Iterable<T>> {
    override fun compare(a: Iterable<T>, b: Iterable<T>): Int = compareTupleIterables(a, b)
}

internal expect fun compareTupleIterables(a: Iterable<Any?>, b: Iterable<Any?>): Int
