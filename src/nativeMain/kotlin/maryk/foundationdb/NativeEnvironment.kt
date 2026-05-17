@file:OptIn(ExperimentalForeignApi::class, ObsoleteWorkersApi::class, ExperimentalAtomicApi::class)

package maryk.foundationdb

import foundationdb.c.fdb_get_max_api_version
import foundationdb.c.fdb_run_network
import foundationdb.c.fdb_select_api_version_impl
import foundationdb.c.fdb_setup_network
import foundationdb.c.fdb_stop_network
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.sched_yield
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

internal object NativeEnvironment {
    private val stateLock = AtomicInt(0)
    private var apiVersion: Int? = null
    private var requestedApiVersion: Int? = null
    private var networkWorker: Worker? = null
    private var networkStopped = false

    fun ensureNetwork(version: Int = DEFAULT_API_VERSION) {
        withStateLock {
            ensureApiVersionLocked(version)
            if (networkWorker != null) return
            check(!networkStopped) { "FoundationDB network has been stopped and cannot be restarted." }
            checkError(fdb_setup_network())
            val worker = Worker.start()
            try {
                worker.execute(TransferMode.SAFE, {}) {
                    checkError(fdb_run_network())
                }
                networkWorker = worker
            } catch (throwable: Throwable) {
                worker.requestTermination().result
                throw throwable
            }
        }
    }

    fun ensureApiVersion(requested: Int) {
        withStateLock {
            ensureApiVersionLocked(requested)
        }
    }

    fun isApiSelected(): Boolean = withStateLock { apiVersion != null }

    fun setNetworkOption(block: () -> Unit) {
        withStateLock {
            block()
        }
    }

    fun shutdown() {
        withStateLock {
            networkWorker?.let {
                checkError(fdb_stop_network())
                it.requestTermination().result
                networkStopped = true
            }
            networkWorker = null
        }
        resetNativeFutureScope()
    }

    private fun ensureApiVersionLocked(requested: Int) {
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

    private inline fun <T> withStateLock(block: () -> T): T {
        while (!stateLock.compareAndSet(0, 1)) {
            sched_yield()
        }
        try {
            return block()
        } finally {
            stateLock.store(0)
        }
    }
}
