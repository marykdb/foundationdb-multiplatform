package maryk.foundationdb.directory

import kotlin.test.Test
import kotlin.test.assertEquals

class DirectoryExceptionTest {
    @Test
    fun directoryVersionExceptionExposesMessage() {
        val message = "Directory layer version mismatch"
        val ex = DirectoryVersionException(message)
        assertEquals(message, ex.message)
    }
}
