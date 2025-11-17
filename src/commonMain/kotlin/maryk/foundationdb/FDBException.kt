package maryk.foundationdb

/**
 * FoundationDB client exception wrapper.
 */
expect open class FDBException internal constructor(
    message: String,
    code: Int
): RuntimeException {
    fun getCode(): Int
    fun isSuccess(): Boolean
    fun isRetryable(): Boolean
    fun isMaybeCommitted(): Boolean
    fun isRetryableNotCommitted(): Boolean
    fun retargetClone(): Exception
}
