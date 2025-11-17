package maryk.foundationdb.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.getenv
import platform.posix.mkdtemp

@OptIn(ExperimentalForeignApi::class)
actual fun createTempDirectory(prefix: String): String = memScoped {
    val base = getenv("TMPDIR")?.toKString() ?: "/tmp"
    val templateBytes = "$base/${prefix}_XXXXXX".encodeToByteArray() + byteArrayOf(0)
    templateBytes.usePinned { pinned ->
        val result = mkdtemp(pinned.addressOf(0)) ?: error("mkdtemp failed")
        result.toKString()
    }
}
