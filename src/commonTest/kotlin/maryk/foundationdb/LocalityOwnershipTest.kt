package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocalityOwnershipTest {
    @Test fun localityIteratorsOutliveTheSuppliedTransaction() = FoundationDbTestHarness().runAndReset {
        val transaction = database.createTransaction()
        val iterator = LocalityUtil.openBoundaryKeysIterator(transaction, byteArrayOf(), byteArrayOf(-1))
        transaction.close()
        try { while (iterator.onHasNext().await()) iterator.next() }
        finally { iterator.close() }
    }

    @Test fun localityLookupDoesNotEnableSystemReadsOnTheCaller() = FoundationDbTestHarness().runAndReset {
        val transaction = database.createTransaction()
        try {
            LocalityUtil.getBoundaryKeys(transaction, byteArrayOf(), byteArrayOf(-1)).await()
            val error = assertFailsWith<FDBException> { transaction.get(byteArrayOf(-1) + "/keyServers/".encodeToByteArray()).await() }
            assertEquals(2004, error.getCode())
        } finally { transaction.close() }
    }
}
