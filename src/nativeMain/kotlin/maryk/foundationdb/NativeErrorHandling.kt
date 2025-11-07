@file:OptIn(ExperimentalForeignApi::class)

package maryk.foundationdb

import foundationdb.c.fdb_get_error
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString

internal fun checkError(code: Int) {
    if (code != 0) throw FDBException(code)
}

internal fun errorMessage(code: Int): String =
    fdb_get_error(code)?.toKString() ?: "FoundationDB error $code"
