package maryk.foundationdb

import maryk.foundationdb.async.CloneableException

/**
 * FoundationDB client exception wrapper.
 */
expect open class FDBException constructor(
    message: String,
    code: Int
): RuntimeException, CloneableException {
    fun getCode(): Int
    fun isSuccess(): Boolean
    fun isRetryable(): Boolean
    fun isMaybeCommitted(): Boolean
    fun isRetryableNotCommitted(): Boolean
    override fun retargetClone(): Exception
}
