@file:OptIn(ExperimentalNativeApi::class)

package maryk.foundationdb

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.internal.test.testLauncherEntryPoint
import kotlin.system.exitProcess

/** Stop and join FoundationDB's network worker before process-global destructors run. */
fun runNativeTests(args: Array<String>) {
    val exitCode = try {
        testLauncherEntryPoint(args)
    } finally {
        // Listing tests must not initialize FoundationDB. Legacy tests may have
        // already shut it down; shutdown remains safe when no worker is running.
        if (FDB.isAPIVersionSelected()) FDB.instance().shutdown()
    }
    if (exitCode != 0) exitProcess(exitCode)
}
