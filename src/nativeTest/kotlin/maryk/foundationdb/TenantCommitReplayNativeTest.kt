package maryk.foundationdb

import maryk.foundationdb.tuple.Tuple
import kotlin.test.Test
import kotlin.test.assertEquals

class TenantCommitReplayNativeTest {
    @Test fun appliedCreateAndDeleteCanBeReplayedAfterUnknownCommit() = FoundationDbTestHarness().runAndReset {
        for (create in listOf(true, false)) {
            val name = Tuple.from(namespace, "replay", create)
            if (!create) TenantManagement.createTenant(database, name).await()
            var attempts = 0
            val replayingContext = object : TransactionContext {
                override fun <T> run(block: (Transaction) -> T): T = error("Not used")
                override fun <T> read(block: (ReadTransaction) -> T): T = error("Not used")
                override fun <T> readAsync(block: (ReadTransaction) -> FdbFuture<T>): FdbFuture<T> = error("Not used")
                override fun <T> runAsync(block: (Transaction) -> FdbFuture<T>): FdbFuture<T> = fdbFuture {
                    suspend fun attempt(): T {
                        attempts++
                        val transaction = database.createTransaction()
                        return try {
                            val value = block(transaction).await()
                            transaction.commit().await()
                            value
                        } finally { transaction.close() }
                    }
                    // Apply the first commit but discard its acknowledgement, then replay.
                    attempt()
                    attempt()
                }
            }
            try {
                TenantManagement.changeTenant(replayingContext, name.pack(), create).await()
                assertEquals(2, attempts)
                val listed = TenantManagement.listTenants(database, name, Tuple.from(namespace + "~"), 1).await()
                assertEquals(create, listed.any { it.key.contentEquals(name.pack()) })
            } finally { database.run { TenantManagement.deleteTenant(it, name) } }
        }
    }
}
