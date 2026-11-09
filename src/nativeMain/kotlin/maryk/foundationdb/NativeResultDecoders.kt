@file:OptIn(ExperimentalForeignApi::class)

package maryk.foundationdb

import foundationdb.c.FDBFuture
import foundationdb.c.FDBKeyRange
import foundationdb.c.fdb_future_get_bool
import foundationdb.c.fdb_future_get_int64
import foundationdb.c.fdb_future_get_key
import foundationdb.c.fdb_future_get_keyrange_array
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.get
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.value

internal fun nativeKeyFuture(pointer: CPointer<FDBFuture>?): FdbFuture<ByteArray> =
    NativeFuture(requireNotNull(pointer)) { native ->
        val key = allocPointerTo<UByteVar>()
        val size = alloc<IntVar>()
        checkError(fdb_future_get_key(native, key.ptr, size.ptr))
        if (size.value == 0) byteArrayOf() else key.value!!.readBytes(size.value)
    }

internal fun nativeBooleanFuture(pointer: CPointer<FDBFuture>?): FdbFuture<Boolean> =
    NativeFuture(requireNotNull(pointer)) { native ->
        val result = alloc<IntVar>()
        checkError(fdb_future_get_bool(native, result.ptr))
        result.value != 0
    }

internal fun nativeLongFuture(pointer: CPointer<FDBFuture>?): FdbFuture<Long> =
    NativeFuture(requireNotNull(pointer)) { native ->
        val result = alloc<LongVar>()
        checkError(fdb_future_get_int64(native, result.ptr))
        result.value
    }

internal fun nativeRangesFuture(pointer: CPointer<FDBFuture>?, cleanup: () -> Unit = {}): FdbFuture<List<Range>> =
    NativeFuture(requireNotNull(pointer), onCleanup = cleanup) { native ->
        val ranges = allocPointerTo<FDBKeyRange>()
        val size = alloc<IntVar>()
        checkError(fdb_future_get_keyrange_array(native, ranges.ptr, size.ptr))
        List(size.value) { index ->
            val range = ranges.value!![index]
            Range(if (range.begin_key_length == 0) byteArrayOf() else range.begin_key!!.readBytes(range.begin_key_length),
                if (range.end_key_length == 0) byteArrayOf() else range.end_key!!.readBytes(range.end_key_length))
        }
    }
