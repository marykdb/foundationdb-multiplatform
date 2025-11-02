package maryk.foundationdb

/**
 * FoundationDB client exception wrapper.
 */
expect open class FDBException : RuntimeException {
    fun getCode(): Int
    fun isSuccess(): Boolean
    fun isRetryable(): Boolean
    fun isMaybeCommitted(): Boolean
    fun isRetryableNotCommitted(): Boolean
    fun retargetClone(): Exception
}

val FDBException.code: Int
    get() = getCode()
