@file:OptIn(ExperimentalForeignApi::class)

package maryk.foundationdb

import foundationdb.c.FDB_NET_OPTION_ENABLE_SLOW_TASK_PROFILING

import foundationdb.c.FDB_NET_OPTION_DISABLE_CLIENT_STATISTICS_LOGGING

import foundationdb.c.FDB_NET_OPTION_FAIL_INCOMPATIBLE_CLIENT

import foundationdb.c.FDB_NET_OPTION_IGNORE_EXTERNAL_CLIENT_FAILURES

import foundationdb.c.FDB_NET_OPTION_RETAIN_CLIENT_LIBRARY_COPIES

import foundationdb.c.FDB_NET_OPTION_FUTURE_VERSION_CLIENT_LIBRARY

import foundationdb.c.FDB_NET_OPTION_CLIENT_THREADS_PER_VERSION

import foundationdb.c.FDB_NET_OPTION_DISABLE_LOCAL_CLIENT

import foundationdb.c.FDB_NET_OPTION_EXTERNAL_CLIENT_DIRECTORY

import foundationdb.c.FDB_NET_OPTION_EXTERNAL_CLIENT_LIBRARY

import foundationdb.c.FDB_NET_OPTION_CALLBACKS_ON_EXTERNAL_THREADS

import foundationdb.c.FDB_NET_OPTION_DISABLE_MULTI_VERSION_CLIENT_API

import foundationdb.c.FDB_NET_OPTION_TLS_PASSWORD

import foundationdb.c.FDB_NET_OPTION_TLS_CA_PATH

import foundationdb.c.FDB_NET_OPTION_TLS_CA_BYTES

import foundationdb.c.FDB_NET_OPTION_BUGGIFY_SECTION_FIRED_PROBABILITY

import foundationdb.c.FDB_NET_OPTION_BUGGIFY_SECTION_ACTIVATED_PROBABILITY

import foundationdb.c.FDB_NET_OPTION_TLS_PLUGIN

import foundationdb.c.FDB_NET_OPTION_CLUSTER_FILE

import foundationdb.c.FDB_NET_OPTION_LOCAL_ADDRESS

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
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value

actual class NetworkOptions internal constructor(private val native: Unit = Unit, private val customConsumer: OptionConsumer? = null) {
    actual constructor(consumer: OptionConsumer) : this(Unit, consumer)

    private fun set(option: UInt) {
        customConsumer?.let { it.setOption(option.toInt(), null); return }
        NativeEnvironment.setNetworkOption {
            checkError(fdb_network_set_option(option, null, 0))
        }
    }

    private fun set(option: UInt, bytes: ByteArray) {
        customConsumer?.let { it.setOption(option.toInt(), bytes); return }
        NativeEnvironment.setNetworkOption {
            bytes.withPointer { ptr, length ->
                checkError(fdb_network_set_option(option, ptr, length))
            }
        }
    }

    private fun set(option: UInt, value: String) = set(option, value.encodeToByteArray())

    private fun set(option: UInt, value: Long) = set(option, ByteArrayUtil.encodeInt(value))

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
    @Deprecated("Deprecated upstream; retained for FoundationDB API compatibility.")
    actual fun setLocalAddress(value: String) = set(FDB_NET_OPTION_LOCAL_ADDRESS, value)
    @Deprecated("Deprecated upstream; retained for FoundationDB API compatibility.")
    actual fun setClusterFile(value: String) = set(FDB_NET_OPTION_CLUSTER_FILE, value)
    @Deprecated("Deprecated upstream; retained for FoundationDB API compatibility.")
    actual fun setTLSPlugin(value: String) = set(FDB_NET_OPTION_TLS_PLUGIN, value)
    actual fun setBuggifySectionActivatedProbability(value: Long) = set(FDB_NET_OPTION_BUGGIFY_SECTION_ACTIVATED_PROBABILITY, value)
    actual fun setBuggifySectionFiredProbability(value: Long) = set(FDB_NET_OPTION_BUGGIFY_SECTION_FIRED_PROBABILITY, value)
    actual fun setTLSCaBytes(value: ByteArray) = set(FDB_NET_OPTION_TLS_CA_BYTES, value)
    actual fun setTLSCaPath(value: String) = set(FDB_NET_OPTION_TLS_CA_PATH, value)
    actual fun setTLSPassword(value: String) = set(FDB_NET_OPTION_TLS_PASSWORD, value)
    actual fun setDisableMultiVersionClientApi() = set(FDB_NET_OPTION_DISABLE_MULTI_VERSION_CLIENT_API)
    actual fun setCallbacksOnExternalThreads() = set(FDB_NET_OPTION_CALLBACKS_ON_EXTERNAL_THREADS)
    actual fun setExternalClientLibrary(value: String) = set(FDB_NET_OPTION_EXTERNAL_CLIENT_LIBRARY, value)
    actual fun setExternalClientDirectory(value: String) = set(FDB_NET_OPTION_EXTERNAL_CLIENT_DIRECTORY, value)
    actual fun setDisableLocalClient() = set(FDB_NET_OPTION_DISABLE_LOCAL_CLIENT)
    actual fun setClientThreadsPerVersion(value: Long) = set(FDB_NET_OPTION_CLIENT_THREADS_PER_VERSION, value)
    actual fun setFutureVersionClientLibrary(value: String) = set(FDB_NET_OPTION_FUTURE_VERSION_CLIENT_LIBRARY, value)
    actual fun setRetainClientLibraryCopies() = set(FDB_NET_OPTION_RETAIN_CLIENT_LIBRARY_COPIES)
    actual fun setIgnoreExternalClientFailures() = set(FDB_NET_OPTION_IGNORE_EXTERNAL_CLIENT_FAILURES)
    actual fun setFailIncompatibleClient() = set(FDB_NET_OPTION_FAIL_INCOMPATIBLE_CLIENT)
    actual fun setDisableClientStatisticsLogging() = set(FDB_NET_OPTION_DISABLE_CLIENT_STATISTICS_LOGGING)
    @Deprecated("Deprecated upstream; retained for FoundationDB API compatibility.")
    actual fun setEnableSlowTaskProfiling() = set(FDB_NET_OPTION_ENABLE_SLOW_TASK_PROFILING)
    actual fun getOptionConsumer(): OptionConsumer = OptionConsumer { code, value ->
        if (value == null) set(code.toUInt()) else set(code.toUInt(), value)
    }

}
