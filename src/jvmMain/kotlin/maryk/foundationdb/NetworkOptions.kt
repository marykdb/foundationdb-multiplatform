package maryk.foundationdb

import com.apple.foundationdb.NetworkOptions as JavaNetworkOptions

actual class NetworkOptions internal constructor(
    internal val delegate: com.apple.foundationdb.NetworkOptions
) {
    actual constructor(consumer: OptionConsumer) : this(JavaNetworkOptions { code, value -> consumer.setOption(code, value) })

    actual fun setTraceEnable(path: String?) {
        delegate.setTraceEnable(path)
    }

    actual fun setTraceRollSize(bytes: Long) {
        delegate.setTraceRollSize(bytes)
    }

    actual fun setTraceMaxLogsSize(bytes: Long) {
        delegate.setTraceMaxLogsSize(bytes)
    }

    actual fun setTraceLogGroup(logGroup: String) {
        delegate.setTraceLogGroup(logGroup)
    }

    actual fun setTraceFormat(format: String) {
        delegate.setTraceFormat(format)
    }

    actual fun setTraceClockSource(clock: String) {
        delegate.setTraceClockSource(clock)
    }

    actual fun setTraceFileIdentifier(identifier: String) {
        delegate.setTraceFileIdentifier(identifier)
    }

    actual fun setTraceShareAmongClientThreads() {
        delegate.setTraceShareAmongClientThreads()
    }

    actual fun setTraceInitializeOnSetup() {
        delegate.setTraceInitializeOnSetup()
    }

    actual fun setTracePartialFileSuffix(suffix: String) {
        delegate.setTracePartialFileSuffix(suffix)
    }

    actual fun setKnob(knob: String) {
        delegate.setKnob(knob)
    }

    actual fun setTLSCertBytes(bytes: ByteArray) {
        delegate.setTLSCertBytes(bytes)
    }

    actual fun setTLSCertPath(path: String) {
        delegate.setTLSCertPath(path)
    }

    actual fun setTLSKeyBytes(bytes: ByteArray) {
        delegate.setTLSKeyBytes(bytes)
    }

    actual fun setTLSKeyPath(path: String) {
        delegate.setTLSKeyPath(path)
    }

    actual fun setTLSVerifyPeers(pattern: ByteArray) {
        delegate.setTLSVerifyPeers(pattern)
    }

    actual fun setBuggifyEnable() {
        delegate.setBuggifyEnable()
    }

    actual fun setBuggifyDisable() {
        delegate.setBuggifyDisable()
    }

    actual fun setClientBuggifyEnable() {
        delegate.setClientBuggifyEnable()
    }

    actual fun setClientBuggifyDisable() {
        delegate.setClientBuggifyDisable()
    }

    actual fun setClientBuggifySectionActivatedProbability(percentage: Long) {
        delegate.setClientBuggifySectionActivatedProbability(percentage)
    }

    actual fun setClientBuggifySectionFiredProbability(percentage: Long) {
        delegate.setClientBuggifySectionFiredProbability(percentage)
    }

    actual fun setDistributedClientTracer(value: String) {
        delegate.setDistributedClientTracer(value)
    }

    actual fun setClientTmpDir(path: String) {
        delegate.setClientTmpDir(path)
    }

    actual fun setEnableRunLoopProfiling() {
        delegate.setEnableRunLoopProfiling()
    }

    actual fun setDisableClientBypass() {
        delegate.setDisableClientBypass()
    }
    @Deprecated("Deprecated upstream; retained for FoundationDB API compatibility.")
    actual fun setLocalAddress(value: String) = delegate.setLocalAddress(value)
    @Deprecated("Deprecated upstream; retained for FoundationDB API compatibility.")
    actual fun setClusterFile(value: String) = delegate.setClusterFile(value)
    @Deprecated("Deprecated upstream; retained for FoundationDB API compatibility.")
    actual fun setTLSPlugin(value: String) = delegate.setTLSPlugin(value)
    actual fun setBuggifySectionActivatedProbability(value: Long) = delegate.setBuggifySectionActivatedProbability(value)
    actual fun setBuggifySectionFiredProbability(value: Long) = delegate.setBuggifySectionFiredProbability(value)
    actual fun setTLSCaBytes(value: ByteArray) = delegate.setTLSCaBytes(value)
    actual fun setTLSCaPath(value: String) = delegate.setTLSCaPath(value)
    actual fun setTLSPassword(value: String) = delegate.setTLSPassword(value)
    actual fun setDisableMultiVersionClientApi() = delegate.setDisableMultiVersionClientApi()
    actual fun setCallbacksOnExternalThreads() = delegate.setCallbacksOnExternalThreads()
    actual fun setExternalClientLibrary(value: String) = delegate.setExternalClientLibrary(value)
    actual fun setExternalClientDirectory(value: String) = delegate.setExternalClientDirectory(value)
    actual fun setDisableLocalClient() = delegate.setDisableLocalClient()
    actual fun setClientThreadsPerVersion(value: Long) = delegate.setClientThreadsPerVersion(value)
    actual fun setFutureVersionClientLibrary(value: String) = delegate.setFutureVersionClientLibrary(value)
    actual fun setRetainClientLibraryCopies() = delegate.setRetainClientLibraryCopies()
    actual fun setIgnoreExternalClientFailures() = delegate.setIgnoreExternalClientFailures()
    actual fun setFailIncompatibleClient() = delegate.setFailIncompatibleClient()
    actual fun setDisableClientStatisticsLogging() = delegate.setDisableClientStatisticsLogging()
    @Deprecated("Deprecated upstream; retained for FoundationDB API compatibility.")
    actual fun setEnableSlowTaskProfiling() = delegate.setEnableSlowTaskProfiling()
    actual fun getOptionConsumer(): OptionConsumer = OptionConsumer { code, value -> delegate.optionConsumer.setOption(code, value) }

}
