package maryk.foundationdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PointConflictTest {
    @Test fun readConflictKeysDoNotCoverDescendants() = FoundationDbTestHarness().runAndReset {
        for (conditional in listOf(false, true)) {
            val exact = key("point", conditional.toString())
            val observer = database.createTransaction()
            try {
                observer.getReadVersion().await()
                if (conditional) assertTrue(observer.addReadConflictKeyIfNotSnapshot(exact))
                else observer.addReadConflictKey(exact)
                observer.set(key("observer", conditional.toString()), byteArrayOf(1))
                database.run { it.set(exact + byteArrayOf(1), byteArrayOf(2)) }
                observer.commit().await()
            } finally { observer.close() }
        }
    }

    @Test fun writeConflictKeysDoNotCoverDescendants() = FoundationDbTestHarness().runAndReset {
        val exact = key("point")
        val observer = database.createTransaction()
        val writer = database.createTransaction()
        try {
            observer.get(exact + byteArrayOf(1)).await()
            observer.set(key("observer"), byteArrayOf(1))
            writer.addWriteConflictKey(exact)
            writer.set(key("writer"), byteArrayOf(2))
            writer.commit().await()
            observer.commit().await()
        } finally { writer.close(); observer.close() }
    }

    @Test fun emptyPointKeysAndSnapshotNoOpAreSupported() = FoundationDbTestHarness().runAndReset {
        val transaction = database.createTransaction()
        try {
            transaction.addReadConflictKey(byteArrayOf())
            transaction.addWriteConflictKey(byteArrayOf())
            assertTrue(transaction.addReadConflictKeyIfNotSnapshot(byteArrayOf()))
            assertFalse(transaction.snapshot().addReadConflictKeyIfNotSnapshot(byteArrayOf()))
        } finally { transaction.close() }
    }

    @Test fun exactPointReadAndWriteConflictsStillAbort() = FoundationDbTestHarness().runAndReset {
        for (explicitWrite in listOf(false, true)) {
            val exact = key("point", explicitWrite.toString())
            val observer = database.createTransaction()
            val writer = database.createTransaction()
            try {
                observer.getReadVersion().await()
                assertTrue(observer.addReadConflictKeyIfNotSnapshot(exact))
                observer.set(key("observer", explicitWrite.toString()), byteArrayOf(1))
                if (explicitWrite) {
                    writer.addWriteConflictKey(exact)
                    writer.set(key("writer"), byteArrayOf(2))
                } else writer.set(exact, byteArrayOf(2))
                writer.commit().await()
                assertEquals(1020, assertFailsWith<FDBException> { observer.commit().await() }.getCode())
            } finally { writer.close(); observer.close() }
        }
    }

    @Test fun explicitPrefixRangesStillConflictWithDescendants() = FoundationDbTestHarness().runAndReset {
        val prefix = key("prefix")
        val range = Range.startsWith(prefix)
        val observer = database.createTransaction()
        try {
            observer.getReadVersion().await()
            observer.addReadConflictRange(range.begin, range.end)
            observer.set(key("observer"), byteArrayOf(1))
            database.run { it.set(prefix + byteArrayOf(1), byteArrayOf(2)) }
            assertEquals(1020, assertFailsWith<FDBException> { observer.commit().await() }.getCode())
        } finally { observer.close() }
    }
}
