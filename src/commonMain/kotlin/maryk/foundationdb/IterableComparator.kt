package maryk.foundationdb

expect class IterableComparator<T : Comparable<T>>() : Comparator<Iterable<T>> {
    override fun compare(a: Iterable<T>, b: Iterable<T>): Int
}
