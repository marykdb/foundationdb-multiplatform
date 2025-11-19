@file:OptIn(ExperimentalForeignApi::class, ObsoleteWorkersApi::class)

package maryk.foundationdb

import foundationdb.c.fdb_get_max_api_version
import foundationdb.c.fdb_run_network
import foundationdb.c.fdb_select_api_version_impl
import foundationdb.c.fdb_setup_network
import foundationdb.c.fdb_stop_network
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

internal object NativeEnvironment {
    private var apiVersion: Int? = null
    private var requestedApiVersion: Int? = null
    private var networkWorker: Worker? = null

    fun ensureNetwork(version: Int = DEFAULT_API_VERSION) {
        ensureApiVersion(version)
        if (networkWorker != null) return
        checkError(fdb_setup_network())
        val worker = Worker.start()
        worker.execute(TransferMode.SAFE, {}) {
            checkError(fdb_run_network())
        }
        networkWorker = worker
    }

    fun ensureApiVersion(requested: Int) {
        require(requested > 0) { "API version must be positive" }
        val maxSupported = fdb_get_max_api_version()
        val effective = minOf(requested, maxSupported)
        if (apiVersion == null) {
            checkError(fdb_select_api_version_impl(requested, effective))
            apiVersion = effective
            requestedApiVersion = requested
        } else if (apiVersion != effective) {
            throw IllegalStateException(
                "FoundationDB API version already selected (${requestedApiVersion ?: apiVersion})."
            )
        }
    }

    fun isApiSelected(): Boolean = apiVersion != null

    fun shutdown() {
        networkWorker?.let {
            checkError(fdb_stop_network())
            it.requestTermination().result
        }
        networkWorker = null
        resetNativeFutureScope()
    }
}
