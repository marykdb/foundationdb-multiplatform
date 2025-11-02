package maryk.foundationdb

/**
 * Controls batching behaviour for range reads.
 */
expect enum class StreamingMode {
    WANT_ALL,
    ITERATOR,
    EXACT,
    SMALL,
    MEDIUM,
    LARGE,
    SERIAL
}
