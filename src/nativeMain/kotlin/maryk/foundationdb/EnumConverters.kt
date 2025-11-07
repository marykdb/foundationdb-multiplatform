@file:OptIn(ExperimentalForeignApi::class)

package maryk.foundationdb

import foundationdb.c.FDBConflictRangeType
import foundationdb.c.FDBMutationType
import foundationdb.c.FDBStreamingMode
import foundationdb.c.FDB_CONFLICT_RANGE_TYPE_READ
import foundationdb.c.FDB_CONFLICT_RANGE_TYPE_WRITE
import foundationdb.c.FDB_MUTATION_TYPE_ADD
import foundationdb.c.FDB_MUTATION_TYPE_APPEND_IF_FITS
import foundationdb.c.FDB_MUTATION_TYPE_BIT_AND
import foundationdb.c.FDB_MUTATION_TYPE_BIT_OR
import foundationdb.c.FDB_MUTATION_TYPE_BIT_XOR
import foundationdb.c.FDB_MUTATION_TYPE_BYTE_MAX
import foundationdb.c.FDB_MUTATION_TYPE_BYTE_MIN
import foundationdb.c.FDB_MUTATION_TYPE_MAX
import foundationdb.c.FDB_MUTATION_TYPE_MIN
import foundationdb.c.FDB_MUTATION_TYPE_SET_VERSIONSTAMPED_KEY
import foundationdb.c.FDB_MUTATION_TYPE_SET_VERSIONSTAMPED_VALUE
import foundationdb.c.FDB_STREAMING_MODE_EXACT
import foundationdb.c.FDB_STREAMING_MODE_ITERATOR
import foundationdb.c.FDB_STREAMING_MODE_LARGE
import foundationdb.c.FDB_STREAMING_MODE_MEDIUM
import foundationdb.c.FDB_STREAMING_MODE_SERIAL
import foundationdb.c.FDB_STREAMING_MODE_SMALL
import foundationdb.c.FDB_STREAMING_MODE_WANT_ALL
import kotlinx.cinterop.ExperimentalForeignApi

internal fun StreamingMode.toNative(): FDBStreamingMode = when (this) {
    StreamingMode.WANT_ALL -> FDB_STREAMING_MODE_WANT_ALL
    StreamingMode.ITERATOR -> FDB_STREAMING_MODE_ITERATOR
    StreamingMode.EXACT -> FDB_STREAMING_MODE_EXACT
    StreamingMode.SMALL -> FDB_STREAMING_MODE_SMALL
    StreamingMode.MEDIUM -> FDB_STREAMING_MODE_MEDIUM
    StreamingMode.LARGE -> FDB_STREAMING_MODE_LARGE
    StreamingMode.SERIAL -> FDB_STREAMING_MODE_SERIAL
}

internal fun ConflictRangeType.toNative(): FDBConflictRangeType = when (this) {
    ConflictRangeType.READ -> FDB_CONFLICT_RANGE_TYPE_READ
    ConflictRangeType.WRITE -> FDB_CONFLICT_RANGE_TYPE_WRITE
}

internal fun MutationType.toNative(): FDBMutationType = when (this) {
    MutationType.ADD -> FDB_MUTATION_TYPE_ADD
    MutationType.AND -> FDB_MUTATION_TYPE_BIT_AND
    MutationType.OR -> FDB_MUTATION_TYPE_BIT_OR
    MutationType.XOR -> FDB_MUTATION_TYPE_BIT_XOR
    MutationType.MAX -> FDB_MUTATION_TYPE_MAX
    MutationType.MIN -> FDB_MUTATION_TYPE_MIN
    MutationType.BYTE_MIN -> FDB_MUTATION_TYPE_BYTE_MIN
    MutationType.BYTE_MAX -> FDB_MUTATION_TYPE_BYTE_MAX
    MutationType.SET_VERSIONSTAMPED_KEY -> FDB_MUTATION_TYPE_SET_VERSIONSTAMPED_KEY
    MutationType.SET_VERSIONSTAMPED_VALUE -> FDB_MUTATION_TYPE_SET_VERSIONSTAMPED_VALUE
    MutationType.APPEND_IF_FITS -> FDB_MUTATION_TYPE_APPEND_IF_FITS
}
