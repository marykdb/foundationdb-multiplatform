@file:OptIn(ExperimentalForeignApi::class)

package maryk.foundationdb

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned

private val emptyBuffer = ByteArray(1)

internal inline fun <T> ByteArray.withPointer(block: (CPointer<UByteVar>, Int) -> T): T =
    if (isEmpty()) {
        emptyBuffer.usePinned { pinned -> block(pinned.addressOf(0).reinterpret(), 0) }
    } else {
        usePinned { pinned -> block(pinned.addressOf(0).reinterpret(), size) }
    }

internal fun Boolean.toFdbBool(): Int = if (this) 1 else 0

internal fun ByteArray.nextKey(): ByteArray {
    for (i in size - 1 downTo 0) {
        val byte = this[i].toInt() and 0xFF
        if (byte != 0xFF) {
            val copy = copyOf(i + 1)
            copy[i] = (byte + 1).toByte()
            return copy
        }
    }
    return this + 0.toByte()
}
