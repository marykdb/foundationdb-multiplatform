package maryk.foundationdb

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import maryk.foundationdb.tuple.Tuple
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TenantStreamingTest {
    @Test fun streamingListAndColdFlowAgreeWithRealTenantMetadata() = FoundationDbTestHarness().runAndReset {
        val begin = Tuple.from(namespace)
        val end = Tuple.from(namespace + "~")
        val names = (0..2).map { Tuple.from(namespace, it) }
        try {
            for (name in names) TenantManagement.createTenant(database, name).await()
            val rows = mutableListOf<KeyValue>()
            val iterator = TenantManagement.openTenantsIterator(database, begin, end, 2)
            try { while (iterator.onHasNext().await()) rows += iterator.next() }
            finally { iterator.close() }
            assertEquals(2, rows.size)
            assertContentEquals(names.first().pack(), rows.first().key)
            assertEquals(TenantManagement.listTenants(database, begin, end, 2).await(), rows)
            assertFalse(iterator.onHasNext().await())
            val flow = TenantManagement.listTenantsFlow(database, begin, end)
            assertEquals(1, flow.take(1).toList().size)
            assertEquals(3, flow.toList().size)
            assertEquals(3, flow.toList().size)
            val raw = TenantManagement.openTenantsIterator(database, begin.pack(), end.pack(), 1)
            try {
                assertTrue(raw.onHasNext().await())
                assertContentEquals(names.first().pack(), raw.next().key)
                raw.cancel()
                assertFalse(raw.hasNext())
            } finally { raw.close() }
        } finally { for (name in names) TenantManagement.deleteTenant(database, name).await() }
    }
}
