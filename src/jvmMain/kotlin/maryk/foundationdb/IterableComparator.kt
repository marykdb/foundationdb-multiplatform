package maryk.foundationdb

actual class IterableComparator<T : Comparable<T>> actual constructor() : Comparator<Iterable<T>> {
    actual override fun compare(a: Iterable<T>, b: Iterable<T>): Int {
        val it1 = a.iterator()
        val it2 = b.iterator()
        while (true) {
            val hasNext1 = it1.hasNext()
            val hasNext2 = it2.hasNext()
            if (!hasNext1 || !hasNext2) {
                return when {
                    hasNext1 && !hasNext2 -> 1
                    !hasNext1 && hasNext2 -> -1
                    else -> 0
                }
            }
            val comparison = it1.next().compareTo(it2.next())
            if (comparison != 0) return comparison
        }
    }
}
