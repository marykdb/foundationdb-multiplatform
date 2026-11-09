package maryk.foundationdb

import com.apple.foundationdb.Database as JavaDatabase
import com.apple.foundationdb.ReadTransaction as JavaReadTransaction
import com.apple.foundationdb.ReadTransactionContext as JavaReadContext
import com.apple.foundationdb.Transaction as JavaTransaction
import com.apple.foundationdb.TransactionContext as JavaTransactionContext
import com.apple.foundationdb.Tenant as JavaTenant
import com.apple.foundationdb.NetworkOptions as JavaNetworkOptions
import com.apple.foundationdb.DatabaseOptions as JavaDatabaseOptions
import com.apple.foundationdb.TransactionOptions as JavaTransactionOptions
import com.apple.foundationdb.MutationType as JavaMutationType
import com.apple.foundationdb.KeySelector as JavaKeySelector
import com.apple.foundationdb.Range as JavaRange
import com.apple.foundationdb.StreamingMode as JavaStreamingMode
import com.apple.foundationdb.tuple.Tuple as JavaTuple
import maryk.foundationdb.tuple.Tuple
import java.util.concurrent.Executor
import java.util.function.Function
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JavaCoreSignatureTest {
    @Test
    fun portableCoreOverloadsMatchJavaParameterSignatures() {
        val types = mapOf<Class<*>, Class<*>>(JavaDatabase::class.java to Database::class.java,
            JavaReadTransaction::class.java to ReadTransaction::class.java,
            JavaReadContext::class.java to ReadTransactionContext::class.java,
            JavaTransaction::class.java to Transaction::class.java,
            JavaTransactionContext::class.java to TransactionContext::class.java,
            JavaTenant::class.java to Tenant::class.java,
            JavaKeySelector::class.java to KeySelector::class.java,
            JavaRange::class.java to Range::class.java,
            JavaStreamingMode::class.java to StreamingMode::class.java,
            JavaMutationType::class.java to MutationType::class.java,
            JavaTuple::class.java to Tuple::class.java,
            Function::class.java to Function1::class.java)
        for (upstream in listOf(JavaDatabase::class.java, JavaReadTransaction::class.java, JavaTransaction::class.java, JavaTenant::class.java)) {
            val wrapper = types.getValue(upstream)
            for (method in upstream.methods) {
                if (method.name == "getExecutor" || method.parameterTypes.any { Executor::class.java.isAssignableFrom(it) }) continue
                val parameters = method.parameterTypes.map { types[it] ?: it }.toTypedArray()
                assertNotNull(wrapper.getMethod(method.name, *parameters), "${upstream.simpleName}.${method.name}")
            }
        }
    }

    @Test
    fun everyTypedOptionHasTheSameWireCodeAndPayloadAsJava() {
        var expectedCode = -1
        var actualCode = -1
        var expectedBytes: ByteArray? = null
        var actualBytes: ByteArray? = null
        val consumer = OptionConsumer { code, value -> actualCode = code; actualBytes = value }
        val pairs: List<Pair<Any, Any>> = listOf(
            JavaNetworkOptions { code, value -> expectedCode = code; expectedBytes = value } to NetworkOptions(consumer),
            JavaDatabaseOptions { code, value -> expectedCode = code; expectedBytes = value } to DatabaseOptions(consumer),
            JavaTransactionOptions { code, value -> expectedCode = code; expectedBytes = value } to TransactionOptions(consumer))
        for ((javaOptions, wrapper) in pairs) for (method in javaOptions.javaClass.declaredMethods.filter { it.name.startsWith("set") }) {
            val arguments = method.parameterTypes.map { when (it) {
                String::class.java -> "unicode-\u00e9"
                Long::class.javaPrimitiveType -> -123456789L
                ByteArray::class.java -> byteArrayOf(0, 1, -1)
                else -> error("Unexpected option parameter $it")
            } }.toTypedArray()
            method.invoke(javaOptions, *arguments)
            wrapper.javaClass.getMethod(method.name, *method.parameterTypes).invoke(wrapper, *arguments)
            assertEquals(expectedCode, actualCode, method.name)
            assertContentEquals(expectedBytes, actualBytes, method.name)
        }
    }

    @Test
    fun allAtomicNamesAndWireCodesMatchIncludingLegacyAliases() {
        assertEquals(JavaMutationType.values().map { it.name }.toSet(), MutationType.entries.map { it.name }.toSet())
        for (type in JavaMutationType.values()) assertEquals(type.code(), MutationType.valueOf(type.name).code())
    }
}
