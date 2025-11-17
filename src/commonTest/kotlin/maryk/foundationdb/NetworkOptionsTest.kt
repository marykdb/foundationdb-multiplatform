package maryk.foundationdb

import kotlin.test.Test
import maryk.foundationdb.platform.createTempDirectory

class NetworkOptionsTest {
    @Test
    fun configureBasicTracingOptions() {
        ensureApiVersionSelected()
        val tempDir = createTempDirectory("fdb-trace")
        val options = FDB.instance().options()
        options.setTraceLogGroup("maryk-tests")
        options.setTraceEnable(tempDir)
        options.setTraceShareAmongClientThreads()
        options.setTraceInitializeOnSetup()
        options.setClientTmpDir(tempDir)
    }
}
