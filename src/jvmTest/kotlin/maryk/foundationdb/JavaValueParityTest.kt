package maryk.foundationdb

import com.apple.foundationdb.async.AsyncUtil as JavaAsyncUtil
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFalse
import com.apple.foundationdb.MappedKeyValue as JavaMappedKeyValue
import com.apple.foundationdb.KeyValue as JavaKeyValue
import com.apple.foundationdb.tuple.IterableComparator as JavaIterableComparator
import maryk.foundationdb.tuple.Tuple
import maryk.foundationdb.tuple.TupleInteger
import maryk.foundationdb.tuple.Versionstamp
import kotlin.math.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class JavaValueParityTest {
    @OptIn(ExperimentalUuidApi::class)
    @Test fun allSupportedTupleItemKindsCompareLikeJava() {
        val values = listOf<Any?>(null, byteArrayOf(), byteArrayOf(0x80.toByte()), "a", "\uE000", "\uD800\uDC00",
            listOf(null, byteArrayOf(1), 2L), Tuple.from("nested", 1L), -1L, 0, 1L,
            TupleInteger.parse("18446744073709551616"), -0.0f, 0.0f, Float.NaN, -0.0, Double.NaN,
            false, true, Uuid.parse("ffffffff-ffff-ffff-ffff-ffffffffffff"),
            Versionstamp.complete(ByteArray(10), 1), Versionstamp.incomplete(2))
        val comparator = IterableComparator<Any?>()
        val java = JavaIterableComparator()
        for (left in values) for (right in values) {
            val a = listOf(left)
            val b = listOf(right)
            val expected = java.compare(Tuple.fromItems(a).delegate.items, Tuple.fromItems(b).delegate.items).sign
            assertEquals(expected, comparator.compare(a, b).sign, "${left?.let { it::class }} versus ${right?.let { it::class }}")
        }
    }

    @Test fun javaCompletionHandlersLeaveCancelledDependentsInputsRunning() {
        val operations: List<(CompletableFuture<Int>) -> CompletableFuture<*>> = listOf(
            { JavaAsyncUtil.success(it) }, { JavaAsyncUtil.whenReady(it) }, { JavaAsyncUtil.tag(it, "tag") },
            { JavaAsyncUtil.composeExceptionally(it) { CompletableFuture.completedFuture(0) } },
            { JavaAsyncUtil.composeHandle(it) { value, _ -> CompletableFuture.completedFuture(value ?: 0) } },
            { JavaAsyncUtil.composeHandleAsync(it) { value, _ -> CompletableFuture.completedFuture(value ?: 0) } },
        )
        for (operation in operations) {
            val source = CompletableFuture<Int>()
            operation(source).cancel(false)
            assertFalse(source.isCancelled)
            source.complete(7)
        }
    }

    @Test fun mappedEqualityHashingAndAssignabilityMatchJava() {
        fun javaRow(byte: Byte) = JavaMappedKeyValue(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), byteArrayOf(byte),
            listOf(JavaKeyValue(byteArrayOf(5), byteArrayOf(6))))
        fun commonRow(byte: Byte) = MappedKeyValue(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), byteArrayOf(byte),
            listOf(KeyValue(byteArrayOf(5), byteArrayOf(6))))
        for (left in listOf<Byte>(4, 9)) for (right in listOf<Byte>(4, 9)) {
            assertEquals(javaRow(left) == javaRow(right), commonRow(left) == commonRow(right))
            assertEquals(javaRow(left).hashCode(), commonRow(left).hashCode())
        }
        assertTrue(KeyValue::class.java.isAssignableFrom(MappedKeyValue::class.java))
        assertEquals(JavaKeyValue(byteArrayOf(1), byteArrayOf(2)).hashCode(), KeyValue(byteArrayOf(1), byteArrayOf(2)).hashCode())
    }
}
