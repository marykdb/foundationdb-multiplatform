@file:OptIn(ExperimentalForeignApi::class)

package maryk.foundationdb

import maryk.foundationdb.async.CloneableException

import foundationdb.c.fdb_error_predicate
import kotlinx.cinterop.ExperimentalForeignApi

actual open class FDBException actual constructor(
    message: String,
    private val code: Int,
) : RuntimeException(message), CloneableException {
    constructor(code: Int) : this(errorMessage(code), code)

    actual fun getCode(): Int = code
    actual fun isSuccess(): Boolean = code == 0
    actual fun isRetryable(): Boolean = fdb_error_predicate(ERROR_PREDICATE_RETRYABLE, code) != 0
    actual fun isMaybeCommitted(): Boolean = fdb_error_predicate(ERROR_PREDICATE_MAYBE_COMMITTED, code) != 0
    actual fun isRetryableNotCommitted(): Boolean =
        fdb_error_predicate(ERROR_PREDICATE_RETRYABLE_NOT_COMMITTED, code) != 0
    actual override fun retargetClone(): Exception = FDBException(message ?: errorMessage(code), code)
}
