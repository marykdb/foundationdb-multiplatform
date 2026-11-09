package maryk.foundationdb

actual enum class StreamingMode {
    WANT_ALL,
    ITERATOR,
    EXACT,
    SMALL,
    MEDIUM,
    LARGE,
    SERIAL;

    actual fun code(): Int = ordinal - 2
}
