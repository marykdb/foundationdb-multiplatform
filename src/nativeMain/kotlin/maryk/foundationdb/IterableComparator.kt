package maryk.foundationdb

import maryk.foundationdb.tuple.compareTupleItems

internal actual fun compareTupleIterables(a: Iterable<Any?>, b: Iterable<Any?>): Int = compareTupleItems(a, b)
