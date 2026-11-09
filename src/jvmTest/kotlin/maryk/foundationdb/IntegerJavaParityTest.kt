package maryk.foundationdb

import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import maryk.foundationdb.jvm.createTransaction
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import maryk.foundationdb.jvm.packInto
import maryk.foundationdb.jvm.stream
import maryk.foundationdb.jvm.fromStream
import maryk.foundationdb.jvm.asJavaEventKeeper
import maryk.foundationdb.jvm.asJavaEvent
import maryk.foundationdb.tuple.Tuple
import maryk.foundationdb.tuple.TupleInteger
import maryk.foundationdb.tuple.toJavaBigInteger
import maryk.foundationdb.tuple.toTupleInteger
import com.apple.foundationdb.tuple.Tuple as JavaTuple

class IntegerJavaParityTest {
    @Test fun executorAndMetricsFactoriesWireTheOfficialClient() = FoundationDbTestHarness().runAndReset {
        val metrics = MapEventKeeper()
        val transaction = database.createTransaction(Executor { it.run() }, metrics)
        try {
            transaction.set(key("metrics"), byteArrayOf(1))
            transaction.get(key("metrics")).await()
            transaction.commit().await()
            assertEquals(true, metrics.getCount(FdbEvent.JNI_CALL) > 0)
        } finally { transaction.close() }
    }

    @Test fun integersMatchOfficialJavaEncoding() {
        val random = Random(42)
        for (size in listOf(1, 7, 8, 9, 16, 254, 255)) repeat(30) {
            val magnitude = ByteArray(size).also(random::nextBytes)
            for (sign in listOf(-1, 1)) {
                val java = BigInteger(sign, magnitude)
                val common = java.toTupleInteger()
                assertEquals(java, common.toJavaBigInteger())
                assertEquals(java.toString(), common.toString())
                assertEquals(common, TupleInteger.parse(java.toString()))
                assertContentEquals(JavaTuple.from(java).pack(), Tuple.from(common).pack())
            }
        }
    }
    @Test fun bufferStreamAndCustomEventBridgesPreserveCommonTypes() {
        val tuple = Tuple.from(BigInteger.ONE.shiftLeft(100).toTupleInteger(), "x")
        val buffer = ByteBuffer.allocate(tuple.getPackedSize())
        tuple.packInto(buffer)
        assertContentEquals(tuple.pack(), buffer.array())
        tuple.stream().use { assertEquals(tuple, Tuple.fromStream(it)) }
        val event = object : Event { override val name = "custom" }
        val metrics = MapEventKeeper()
        val bridge = metrics.asJavaEventKeeper()
        bridge.count(event.asJavaEvent(), 4)
        assertEquals(4, metrics.getCount(event))
    }
}
