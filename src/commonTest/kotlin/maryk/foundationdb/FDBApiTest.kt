package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FDBApiTest {
    @Test
    fun openDatabaseAndConfigureWarnings() {
        ensureApiVersionSelected()
        val fdb = FDB.instance()
        assertEquals(ApiVersion.LATEST, fdb.getAPIVersion())
        val defaultDb = fdb.open()
        defaultDb.close()

        fdb.setUnclosedWarning(false)
        fdb.setUnclosedWarning(true)

        val reopened = fdb.open()
        assertNotNull(reopened.options())
        reopened.close()
        assertTrue(FDB.isAPIVersionSelected())
    }
}
