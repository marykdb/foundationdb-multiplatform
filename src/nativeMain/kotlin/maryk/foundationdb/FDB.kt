@file:OptIn(ExperimentalForeignApi::class)

package maryk.foundationdb

import foundationdb.c.FDBDatabase
import foundationdb.c.fdb_create_database
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value

actual class FDB private constructor() {
    actual fun options(): NetworkOptions = NetworkOptions()

    actual fun open(): Database = openInternal(null)

    actual fun open(clusterFilePath: String): Database = openInternal(clusterFilePath)

    private fun openInternal(clusterFile: String?): Database = memScoped {
        NativeEnvironment.ensureNetwork()
        val outDatabase = allocPointerTo<FDBDatabase>()
        checkError(fdb_create_database(clusterFile, outDatabase.ptr))
        val pointer = outDatabase.value ?: error("fdb_create_database returned null pointer")
        Database(pointer)
    }

    actual fun setUnclosedWarning(enabled: Boolean) {}

    actual fun shutdown() {
        NativeEnvironment.shutdown()
    }

    actual companion object {
        private val instance = FDB()

        actual fun isAPIVersionSelected(): Boolean = NativeEnvironment.isApiSelected()

        actual fun instance(): FDB {
            NativeEnvironment.ensureNetwork()
            return instance
        }

        actual fun selectAPIVersion(version: Int): FDB {
            NativeEnvironment.ensureApiVersion(version)
            return instance
        }
    }
}
