@file:OptIn(ExperimentalForeignApi::class)

package maryk.foundationdb

import foundationdb.c.FDB_NET_OPTION_BUGGIFY_DISABLE
import foundationdb.c.FDB_NET_OPTION_BUGGIFY_ENABLE
import foundationdb.c.FDB_NET_OPTION_CLIENT_BUGGIFY_DISABLE
import foundationdb.c.FDB_NET_OPTION_CLIENT_BUGGIFY_ENABLE
import foundationdb.c.FDB_NET_OPTION_CLIENT_BUGGIFY_SECTION_ACTIVATED_PROBABILITY
import foundationdb.c.FDB_NET_OPTION_CLIENT_BUGGIFY_SECTION_FIRED_PROBABILITY
import foundationdb.c.FDB_NET_OPTION_CLIENT_TMP_DIR
import foundationdb.c.FDB_NET_OPTION_DISABLE_CLIENT_BYPASS
import foundationdb.c.FDB_NET_OPTION_DISTRIBUTED_CLIENT_TRACER
import foundationdb.c.FDB_NET_OPTION_ENABLE_RUN_LOOP_PROFILING
import foundationdb.c.FDB_NET_OPTION_KNOB
import foundationdb.c.FDB_NET_OPTION_TLS_CERT_BYTES
import foundationdb.c.FDB_NET_OPTION_TLS_CERT_PATH
import foundationdb.c.FDB_NET_OPTION_TLS_KEY_BYTES
import foundationdb.c.FDB_NET_OPTION_TLS_KEY_PATH
import foundationdb.c.FDB_NET_OPTION_TLS_VERIFY_PEERS
import foundationdb.c.FDB_NET_OPTION_TRACE_CLOCK_SOURCE
import foundationdb.c.FDB_NET_OPTION_TRACE_ENABLE
import foundationdb.c.FDB_NET_OPTION_TRACE_FILE_IDENTIFIER
import foundationdb.c.FDB_NET_OPTION_TRACE_FORMAT
import foundationdb.c.FDB_NET_OPTION_TRACE_INITIALIZE_ON_SETUP
import foundationdb.c.FDB_NET_OPTION_TRACE_LOG_GROUP
import foundationdb.c.FDB_NET_OPTION_TRACE_MAX_LOGS_SIZE
import foundationdb.c.FDB_NET_OPTION_TRACE_PARTIAL_FILE_SUFFIX
import foundationdb.c.FDB_NET_OPTION_TRACE_ROLL_SIZE
import foundationdb.c.FDB_NET_OPTION_TRACE_SHARE_AMONG_CLIENT_THREADS
import foundationdb.c.fdb_network_set_option
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value

actual class NetworkOptions internal constructor() {
    private fun set(option: UInt) {
        checkError(fdb_network_set_option(option, null, 0))
    }

    private fun set(option: UInt, bytes: ByteArray) {
        bytes.usePinned { pinned ->
            val ptr = pinned.addressOf(0).reinterpret<UByteVar>()
            checkError(fdb_network_set_option(option, ptr, bytes.size))
        }
    }

    private fun set(option: UInt, value: String) = set(option, value.encodeToByteArray())

    private fun set(option: UInt, value: Long) = memScoped {
        val ref = alloc<LongVar>()
        ref.value = value
        val ptr = ref.ptr.reinterpret<UByteVar>()
        checkError(fdb_network_set_option(option, ptr, sizeOf<LongVar>().toInt()))
    }

    actual fun setTraceEnable(path: String?) {
        if (path == null) set(FDB_NET_OPTION_TRACE_ENABLE)
        else set(FDB_NET_OPTION_TRACE_ENABLE, path)
    }

    actual fun setTraceRollSize(bytes: Long) = set(FDB_NET_OPTION_TRACE_ROLL_SIZE, bytes)
    actual fun setTraceMaxLogsSize(bytes: Long) = set(FDB_NET_OPTION_TRACE_MAX_LOGS_SIZE, bytes)
    actual fun setTraceLogGroup(logGroup: String) = set(FDB_NET_OPTION_TRACE_LOG_GROUP, logGroup)
    actual fun setTraceFormat(format: String) = set(FDB_NET_OPTION_TRACE_FORMAT, format)
    actual fun setTraceClockSource(clock: String) = set(FDB_NET_OPTION_TRACE_CLOCK_SOURCE, clock)
    actual fun setTraceFileIdentifier(identifier: String) = set(FDB_NET_OPTION_TRACE_FILE_IDENTIFIER, identifier)
    actual fun setTraceShareAmongClientThreads() = set(FDB_NET_OPTION_TRACE_SHARE_AMONG_CLIENT_THREADS)
    actual fun setTraceInitializeOnSetup() = set(FDB_NET_OPTION_TRACE_INITIALIZE_ON_SETUP)
    actual fun setTracePartialFileSuffix(suffix: String) = set(FDB_NET_OPTION_TRACE_PARTIAL_FILE_SUFFIX, suffix)
    actual fun setKnob(knob: String) = set(FDB_NET_OPTION_KNOB, knob)
    actual fun setTLSCertBytes(bytes: ByteArray) = set(FDB_NET_OPTION_TLS_CERT_BYTES, bytes)
    actual fun setTLSCertPath(path: String) = set(FDB_NET_OPTION_TLS_CERT_PATH, path)
    actual fun setTLSKeyBytes(bytes: ByteArray) = set(FDB_NET_OPTION_TLS_KEY_BYTES, bytes)
    actual fun setTLSKeyPath(path: String) = set(FDB_NET_OPTION_TLS_KEY_PATH, path)
    actual fun setTLSVerifyPeers(pattern: ByteArray) = set(FDB_NET_OPTION_TLS_VERIFY_PEERS, pattern)
    actual fun setBuggifyEnable() = set(FDB_NET_OPTION_BUGGIFY_ENABLE)
    actual fun setBuggifyDisable() = set(FDB_NET_OPTION_BUGGIFY_DISABLE)
    actual fun setClientBuggifyEnable() = set(FDB_NET_OPTION_CLIENT_BUGGIFY_ENABLE)
    actual fun setClientBuggifyDisable() = set(FDB_NET_OPTION_CLIENT_BUGGIFY_DISABLE)
    actual fun setClientBuggifySectionActivatedProbability(percentage: Long) =
        set(FDB_NET_OPTION_CLIENT_BUGGIFY_SECTION_ACTIVATED_PROBABILITY, percentage)
    actual fun setClientBuggifySectionFiredProbability(percentage: Long) =
        set(FDB_NET_OPTION_CLIENT_BUGGIFY_SECTION_FIRED_PROBABILITY, percentage)
    actual fun setDistributedClientTracer(value: String) = set(FDB_NET_OPTION_DISTRIBUTED_CLIENT_TRACER, value)
    actual fun setClientTmpDir(path: String) = set(FDB_NET_OPTION_CLIENT_TMP_DIR, path)
    actual fun setEnableRunLoopProfiling() = set(FDB_NET_OPTION_ENABLE_RUN_LOOP_PROFILING)
    actual fun setDisableClientBypass() = set(FDB_NET_OPTION_DISABLE_CLIENT_BYPASS)
}
