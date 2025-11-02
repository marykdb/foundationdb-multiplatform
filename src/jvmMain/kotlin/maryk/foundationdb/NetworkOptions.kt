package maryk.foundationdb

actual class NetworkOptions internal constructor(
    internal val delegate: com.apple.foundationdb.NetworkOptions
) {
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
}
