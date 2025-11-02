package maryk.foundationdb

actual enum class StreamingMode {
    WANT_ALL,
    ITERATOR,
    EXACT,
    SMALL,
    MEDIUM,
    LARGE,
    SERIAL
}

internal fun StreamingMode.toJava(): com.apple.foundationdb.StreamingMode = when (this) {
    StreamingMode.WANT_ALL -> com.apple.foundationdb.StreamingMode.WANT_ALL
    StreamingMode.ITERATOR -> com.apple.foundationdb.StreamingMode.ITERATOR
    StreamingMode.EXACT -> com.apple.foundationdb.StreamingMode.EXACT
    StreamingMode.SMALL -> com.apple.foundationdb.StreamingMode.SMALL
    StreamingMode.MEDIUM -> com.apple.foundationdb.StreamingMode.MEDIUM
    StreamingMode.LARGE -> com.apple.foundationdb.StreamingMode.LARGE
    StreamingMode.SERIAL -> com.apple.foundationdb.StreamingMode.SERIAL
}
