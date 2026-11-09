package maryk.foundationdb

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Run alone with -PfoundationDbNativeTestFilter=maryk.foundationdb.LegacyApiVersionNativeTest.* */
class LegacyApiVersionNativeTest {
    @Test
    fun selectedOlderApiSurvivesNetworkStartup() = runBlocking {
        val fdb = FDB.selectAPIVersion(710)
        assertEquals(710, fdb.getAPIVersion())
        val database = fdb.open()
        val key = "legacy-api-startup-check".encodeToByteArray()
        try {
            database.run { it.set(key, byteArrayOf(1)) }
            assertContentEquals(byteArrayOf(1), database.readSuspend { it.get(key).await() })
            assertEquals(710, fdb.getAPIVersion())
        } finally {
            try { database.run { it.clear(key) } } finally { try { database.close() } finally { fdb.shutdown() } }
        }
    }
}
