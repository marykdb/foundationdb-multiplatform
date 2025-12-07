package maryk.foundationdb.directory

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import maryk.foundationdb.FoundationDbTestHarness
import maryk.foundationdb.runSuspend
import maryk.foundationdb.subspace.Subspace

class DirectoryManualPrefixTest {
    private val harness = FoundationDbTestHarness()

    @Test
    fun manualPrefixDisallowedByDefault() = harness.runAndReset {
        val layer = DirectoryLayer.getDefault()
        val customPrefix = byteArrayOf(0x19, 0x01)
        assertFailsWith<Throwable> {
            database.runSuspend { txn ->
                layer.create(txn, listOf(namespace, "manual", "nope"), prefix = customPrefix).await()
            }
        }
    }

    @Test
    fun manualPrefixAllowedWhenEnabled() = harness.runAndReset {
        val layer = DirectoryLayer.from(Subspace(byteArrayOf(0xFE.toByte())), Subspace(), allowManualPrefixes = true)
        val path = listOf(namespace, "manual", "ok")
        val customPrefix = byteArrayOf(0x19, 0x02)

        val dir = database.runSuspend { txn -> layer.create(txn, path, prefix = customPrefix).await() }
        assertContentEquals(customPrefix, dir.pack())

        // reopen and ensure prefix is reused
        val reopened = database.runSuspend { txn -> layer.open(txn, path).await() }
        assertContentEquals(customPrefix, reopened.pack())
    }
}
